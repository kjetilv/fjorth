package com.github.kjetilv.fjorth;

import module java.base;
import com.github.kjetilv.fjorth.Interpreter.Result.Failed;

import static com.github.kjetilv.fjorth.Primitives.WORDS;

final class InterpreterImpl implements Interpreter, Loader {

    static InterpreterImpl unsealed(MachineApi machine, Console console) {
        return new InterpreterImpl(
            machine,
            Dictionary.unsealed(WORDS),
            console,
            false
        );
    }

    private final MachineApi machine;

    private final Console console;

    private Dictionary dictionary;

    private Definition definition;

    private final boolean sealed;

    private char[] input = EMPTY_CHARS;

    private int pos;

    private int tokenStart;

    private InterpreterImpl(
        MachineApi machine,
        Dictionary dictionary,
        Console console,
        boolean sealed
    ) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.console = Objects.requireNonNull(console, "out");
        this.sealed = sealed;
    }

    @Override
    public Result interpret(String line) {
        try {
            input = line.toCharArray();
            pos = 0;
            try {
                processTokens();
            } finally {
                console.flush();
            }
            return Result.OK;
        } catch (FjorthException e) {
            try {
                return new Failed(e.multiLineMessage());
            } finally {
                reset();
            }
        }
    }

    @Override
    public void interpretInteractively(String line) {
        switch (interpret(line)) {
            case Result.OK _ -> console.println(" ok");
            case Failed(var message) -> {
                console.println();
                console.println(message);
            }
        }
    }

    @Override
    public Loader loader() {
        return this;
    }

    @Override
    public Result load(Reader reader) {
        try (var lines = new BufferedReader(reader)) {
            String line;
            while ((line = lines.readLine()) != null) {
                var result = interpret(line);
                if (result instanceof Failed failed) {
                    return failed;
                }
            }
            return Result.OK;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + reader, e);
        }
    }

    void openDefinitionBeginLoop() {
        try {
            definition.beginLoop();
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
    }

    void openDefinitionRecurse() {
        Word recurse;
        try {
            recurse = definition.recurse();
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
        append(recurse);
    }

    void openDefinitionEndLoop() {
        try {
            definition.endLoop();
        } catch (FjorthException e) {
            e.fillInStackTrace();
            throw e;
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
    }

    void openDefinitionCloseLoop() {
        try {
            definition.closeLoop();
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
    }

    void openDefinitionBeginTail() {
        try {
            definition.beginTail();
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        } catch (FjorthException e) {
            e.fillInStackTrace();
            throw e;
        }
    }

    InterpreterImpl loadLibrary(String resource) {
        if (load(resource) instanceof Failed(var message)) {
            throw new IllegalStateException("Failed to load library: " + message);
        }
        return this;
    }

    void evaluate(String text) {
        var savedInputChars = input;
        var savedPos = pos;
        var savedTokenStart = tokenStart;
        input = text.toCharArray();
        pos = 0;
        try {
            processTokens();
        } finally {
            input = savedInputChars;
            pos = savedPos;
            tokenStart = savedTokenStart;
        }
    }

    void beginDefinition(String name) {
        if (machine.compiling()) {
            throw new FjorthException(": inside definition");
        }
        definition = new Definition(name);
        machine.compiling(true);
    }

    void endDefinition() {
        if (!machine.compiling()) {
            throw new FjorthException("; outside definition");
        }
        define(definition.seal());
        definition = null;
        machine.compiling(false);
    }

    void append(Word word) {
        try {
            definition.append(word);
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
    }

    void define(Word word) {
        if (sealed) {
            dictionary = dictionary.define(word);
        } else {
            dictionary.insert(word);
        }
    }

    void makeLatestImmediate() {
        Word latest = dictionary.latest();
        Word immediate;
        try {
            immediate = latest.makeImmediate();
        } catch (FjorthException e) {
            e.fillInStackTrace();
            throw e;
        } catch (NullPointerException e) {
            throw new FjorthException("IMMEDIATE: empty dictionary");
        }
        define(immediate);
    }

    String word(String requester) {
        if (nextToken() instanceof String string) {
            return string;
        }
        throw new FjorthException(requester + ": missing name");
    }

    void print(String text) {
        console.print(text);
    }

    void print(char c) {
        console.print(c);
    }

    String readString() {
        return readUntil('"');
    }

    String readUntil(char delimiter) {
        var start = pos;
        var length = input.length;
        while (pos < length && input[pos] != delimiter) {
            pos++;
        }
        var text = new String(input, start, pos - start);
        if (pos < length) {
            pos++;
        }
        return text;
    }

    void readRestOfLine() {
        pos = input.length;
    }

    void execute(Word word) {
        switch (word) {
            case Word.Primitive(var _, var _, var effect) -> effect.apply(this);
            case Word.Value(var _, var effect) -> effect.apply(this);
            case Word.Colon(var _, var _, var body) -> executeAll(body);
            case Word.Literal(var value) -> machine.push(value);
            case Word.Branch(var target) -> outsideDefinition(target);
            case Word.ZeroBranch(var target) -> outsideDefinition(target);
        }
    }

    MachineApi machine() {
        return machine;
    }

    Dictionary dictionary() {
        return dictionary;
    }

    Definition openDefinition() {
        return definition;
    }

    int openDefinitionSize() {
        try {
            return definition.size();
        } catch (NullPointerException e) {
            throw new FjorthException("compilation outside definition");
        }
    }

    Interpreter seal() {
        return new InterpreterImpl(machine, dictionary.seal(), console, true);
    }

    void reset() {
        reset(null);
    }

    void reset(Dictionary dictionary) {
        this.machine.reset();
        this.input = EMPTY_CHARS;
        this.pos = 0;
        this.tokenStart = 0;
        this.definition = null;
        if (dictionary != null) {
            this.dictionary = dictionary;
        }
    }

    private void processTokens() {
        while (true) {
            String token = nextToken();
            if (token == null) {
                return;
            }
            try {
                handle(token);
            } catch (FjorthException e) {
                throw e.locate(new String(input), tokenStart);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process tokens", e);
            }
        }
    }

    private void handle(String token) {
        var word = dictionary.lookup(token);
        if (word == null) {
            var value = number(token);
            if (machine.compiling()) {
                append(Word.literal(value));
            } else {
                machine.push(value);
            }
        } else {
            if (machine.compiling() && !word.immediate()) {
                append(word);
            } else {
                execute(word);
            }
        }
    }

    private void executeAll(Word[] body) {
        for (int pointer = 0; pointer < body.length; ) {
            pointer = switch (body[pointer]) {
                case Word.Branch(var nextPointer) -> nextPointer;
                case Word.ZeroBranch(var nextPointer) -> machine.pop() == 0
                    ? nextPointer
                    : pointer + 1;
                case Word word -> {
                    execute(word);
                    yield pointer + 1;
                }
            };
        }
    }

    private String nextToken() {
        var length = input.length;
        while (pos < length && Character.isWhitespace(input[pos])) {
            pos++;
        }
        if (pos == length) {
            return null;
        }
        tokenStart = pos;
        while (pos < length && !Character.isWhitespace(input[pos])) {
            pos++;
        }
        var token = new String(input, tokenStart, pos - tokenStart);
        if (pos < length) {
            pos++;
        }
        return token;
    }

    private long number(String token) {
        try {
            return Long.parseLong(token, machine.base());
        } catch (NumberFormatException e) {
            throw new FjorthException(token + " ?");
        }
    }

    private static final char[] EMPTY_CHARS = new char[0];

    private static void outsideDefinition(int target) {
        throw new FjorthException("branch outside definition: " + target);
    }
}
