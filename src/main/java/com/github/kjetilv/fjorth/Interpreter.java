package com.github.kjetilv.fjorth;

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

public final class Interpreter {

    private final Machine machine;

    private final PrintWriter out;

    private Dictionary dictionary;

    private Definition definition;

    private String input = "";

    private int pos;

    private int tokenStart;

    public Interpreter(Machine machine, Dictionary dictionary, PrintWriter out) {
        this.machine = machine;
        this.dictionary = dictionary;
        this.out = out;
    }

    public Machine machine() {
        return machine;
    }

    public Dictionary dictionary() {
        return dictionary;
    }

    public void interpret(String line) {
        input = line;
        pos = 0;
        try {
            for (Optional<String> token = nextToken(); token.isPresent(); token = nextToken()) {
                handle(token.get());
            }
        } catch (ForthException e) {
            throw contextualized(e);
        } finally {
            out.flush();
        }
    }

    public void execute(Word word) {
        switch (word) {
            case Word.Primitive primitive -> primitive.effect().apply(this);
            case Word.Colon colon -> run(colon);
            case Word.Literal(long value) -> machine.push(value);
            case Word.Branch _, Word.ZeroBranch _ -> throw new ForthException("branch outside definition");
        }
    }

    public void beginDefinition(String name) {
        if (machine.compiling()) {
            throw new ForthException(": inside definition");
        }
        definition = new Definition(name);
        machine.compiling(true);
    }

    public void endDefinition() {
        if (!machine.compiling()) {
            throw new ForthException("; outside definition");
        }
        define(definition.seal());
        definition = null;
        machine.compiling(false);
    }

    public void append(Word word) {
        open().append(word);
    }

    public void define(Word word) {
        dictionary = dictionary.define(word);
    }

    public void makeLatestImmediate() {
        Word latest = dictionary.latest()
            .orElseThrow(() -> new ForthException("IMMEDIATE: empty dictionary"));
        if (latest instanceof Word.Colon(String name, boolean _, List<Word> body)) {
            dictionary = dictionary.define(new Word.Colon(name, true, body));
        } else {
            throw new ForthException("IMMEDIATE: not a colon definition: " + latest.name());
        }
    }

    public String word(String requester) {
        return nextToken()
            .orElseThrow(() -> new ForthException(requester + ": missing name"));
    }

    public void reset() {
        machine.reset();
        definition = null;
    }

    public void print(String text) {
        out.print(text);
    }

    public void print(char c) {
        out.print(c);
    }

    public String readUntil(char delimiter) {
        int start = pos;
        while (pos < input.length() && input.charAt(pos) != delimiter) {
            pos++;
        }
        String text = input.substring(start, pos);
        if (pos < input.length()) {
            pos++;
        }
        return text;
    }

    public void readRestOfLine() {
        pos = input.length();
    }

    Definition open() {
        if (definition == null) {
            throw new ForthException("compilation outside definition");
        }
        return definition;
    }

    private void run(Word.Colon colon) {
        List<Word> body = colon.body();
        int ip = 0;
        while (ip < body.size()) {
            ip = switch (body.get(ip)) {
                case Word.Branch(int target) -> target;
                case Word.ZeroBranch(int target) -> machine.pop() == 0 ? target : ip + 1;
                case Word word -> {
                    execute(word);
                    yield ip + 1;
                }
            };
        }
    }

    private void handle(String token) {
        Optional<Word> word = dictionary.lookup(token);
        if (word.isPresent()) {
            Word found = word.get();
            if (machine.compiling() && !found.immediate()) {
                append(found);
            } else {
                execute(found);
            }
        } else {
            long value = number(token);
            if (machine.compiling()) {
                append(Word.literal(value));
            } else {
                machine.push(value);
            }
        }
    }

    private ForthException contextualized(ForthException e) {
        return new ForthException(
            e.getMessage() + "\n" + input + "\n" + " ".repeat(tokenStart) + "^"
        );
    }

    private Optional<String> nextToken() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        if (pos == input.length()) {
            return Optional.empty();
        }
        tokenStart = pos;
        while (pos < input.length() && !Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        String token = input.substring(tokenStart, pos);
        if (pos < input.length()) {
            pos++;
        }
        return Optional.of(token);
    }

    private static long number(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw new ForthException(token + " ?");
        }
    }
}
