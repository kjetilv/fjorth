package com.github.kjetilv.fjorth;

import module java.base;

final class Interpreter implements Fjorth {

    private final Machine machine;

    private final Out out;

    private Dictionary dictionary;

    private Definition definition;

    private String input = "";

    private int pos;

    private int tokenStart;

    Interpreter(Machine machine, Dictionary dictionary, Out out) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public Result eval(String line) {
        try {
            interpret(line);
            return OK;
        } catch (FjorthException e) {
            return new Result.Failed(e.getMessage(), this::reset);
        }
    }

    @Override
    public void reset() {
        machine.reset();
        definition = null;
    }

    @Override
    public Out out() {
        return this.out;
    }

    public void beginDefinition(String name) {
        if (machine.compiling()) {
            throw new FjorthException(": inside definition");
        }
        definition = new Definition(name);
        machine.compiling(true);
    }

    public void endDefinition() {
        if (!machine.compiling()) {
            throw new FjorthException("; outside definition");
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
        var latest = dictionary.latest()
            .orElseThrow(() -> new FjorthException("IMMEDIATE: empty dictionary"));
        if (latest instanceof Word.Colon(var name, var _, var body)) {
            dictionary = dictionary.define(new Word.Colon(name, true, body));
        } else {
            throw new FjorthException("IMMEDIATE: not a colon definition: " + latest.name());
        }
    }

    public String word(String requester) {
        return nextToken()
            .orElseThrow(() -> new FjorthException(requester + ": missing name"));
    }

    public void print(String text) {
        out.print(text);
    }

    public void print(char c) {
        out.print(c);
    }

    public String readUntil(char delimiter) {
        var start = pos;
        while (pos < input.length() && input.charAt(pos) != delimiter) {
            pos++;
        }
        var text = input.substring(start, pos);
        if (pos < input.length()) {
            pos++;
        }
        return text;
    }

    public void readRestOfLine() {
        pos = input.length();
    }

    void interpret(String line) {
        input = line;
        pos = 0;
        try {
            tokenLoop();
        } finally {
            out.flush();
        }
    }

    void execute(Word... words) {
        for (var word : words) {
            switch (word) {
                case Word.Primitive primitive -> primitive.effect().apply(this);
                case Word.Colon colon -> run(colon);
                case Word.Literal(var value) -> machine.push(value);
                case Word.Branch _, Word.ZeroBranch _ -> throw new FjorthException("branch outside definition");
            }
        }
    }

    Machine machine() {
        return machine;
    }

    Dictionary dictionary() {
        return dictionary;
    }

    void evaluate(String text) {
        var savedInput = input;
        var savedPos = pos;
        var savedTokenStart = tokenStart;
        input = text;
        pos = 0;
        try {
            tokenLoop();
        } finally {
            input = savedInput;
            pos = savedPos;
            tokenStart = savedTokenStart;
        }
    }

    Definition open() {
        if (definition == null) {
            throw new FjorthException("compilation outside definition");
        }
        return definition;
    }

    private void run(Word.Colon colon) {
        var body = colon.body();
        var ip = 0;
        while (ip < body.size()) {
            ip = switch (body.get(ip)) {
                case Word.Branch(var target) -> target;
                case Word.ZeroBranch(var target) -> machine.pop() == 0 ? target : ip + 1;
                case Word word -> {
                    execute(word);
                    yield ip + 1;
                }
            };
        }
    }

    private void handle(String token) {
        var word = dictionary.lookup(token);
        if (word.isPresent()) {
            var found = word.get();
            if (machine.compiling() && !found.immediate()) {
                append(found);
            } else {
                execute(found);
            }
        } else {
            var value = number(token);
            if (machine.compiling()) {
                append(Word.literal(value));
            } else {
                machine.push(value);
            }
        }
    }

    private void tokenLoop() {
        try {
            for (var token = nextToken(); token.isPresent(); token = nextToken()) {
                handle(token.get());
            }
        } catch (FjorthException e) {
            throw e.locate(input, tokenStart);
        }
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
        var token = input.substring(tokenStart, pos);
        if (pos < input.length()) {
            pos++;
        }
        return Optional.of(token);
    }

    private long number(String token) {
        try {
            return Long.parseLong(token, machine.base());
        } catch (NumberFormatException e) {
            throw new FjorthException(token + " ?");
        }
    }

    private static final Result.OK OK = new Result.OK();
}
