package com.github.kjetilv.fjorth;

import module java.base;

import com.github.kjetilv.fjorth.Interpreter.Result.Failed;

import static java.nio.charset.StandardCharsets.UTF_8;

final class InterpreterImpl implements Interpreter {

    static InterpreterImpl unsealed(MachineImpl machine, Console console) {
        return new InterpreterImpl(
            machine,
            Dictionary.unsealed(Primitives.WORDS),
            console,
            false
        );
    }

    private final MachineImpl machine;

    private final Console console;

    private Dictionary dictionary;

    private Definition definition;

    private final boolean sealed;

    private String input = "";

    private int pos;

    private int tokenStart;

    private InterpreterImpl(MachineImpl machine, Dictionary dictionary, Console console, boolean sealed) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.console = Objects.requireNonNull(console, "out");
        this.sealed = sealed;
    }

    @Override
    public Result interpret(String line) {
        try {
            input = line;
            pos = 0;
            try {
                processTokens();
            } finally {
                console.flush();
            }
            return OK;
        } catch (FjorthException e) {
            try {
                return new Failed(e.getMessage());
            } finally {
                reset();
            }
        }
    }

    InterpreterImpl loadLibrary(String libraryResource) {
        Optional<Failed> failed;
        try (
            var reader = libraryReader(libraryResource)
        ) {
            failed = reader.lines()
                .map(this::interpret)
                .filter(Failed.class::isInstance)
                .map(Failed.class::cast)
                .findFirst();
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + libraryResource, e);
        }
        failed.map(Failed::message)
            .ifPresent(message -> {
                throw new IllegalStateException("Failed to execute library: " + message);
            });
        return this;
    }

    void evaluate(String text) {
        var savedInput = input;
        var savedPos = pos;
        var savedTokenStart = tokenStart;
        input = text;
        pos = 0;
        try {
            processTokens();
        } finally {
            input = savedInput;
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
        open().append(word);
    }

    void define(Word word) {
        if (sealed) {
            dictionary = dictionary.define(word);
        } else {
            dictionary.defineInPlace(word);
        }
    }

    void makeLatestImmediate() {
        var latest = dictionary.latest()
            .orElseThrow(() -> new FjorthException("IMMEDIATE: empty dictionary"));
        if (!(latest instanceof Word.Colon(var name, var _, var body))) {
            throw new FjorthException("IMMEDIATE: not a colon definition: " + latest.name());
        }
        dictionary = dictionary.define(new Word.Colon(name, true, body));
    }

    String word(String requester) {
        var next = nextToken();
        if (next == null) {
            throw new FjorthException(requester + ": missing name");
        }
        return next;
    }

    void print(String text) {
        console.print(text);
    }

    void print(char c) {
        console.print(c);
    }

    String readUntil(char delimiter) {
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

    void readRestOfLine() {
        pos = input.length();
    }

    void execute(Word... words) {
        for (var word : words) {
            switch (word) {
                case Word.Primitive primitive -> primitive.effect().apply(this);
                case Word.Colon colon -> run(colon);
                case Word.Literal(var value) -> machine.push(value);
                case Word.Branch(var _), Word.ZeroBranch(var _) ->
                    throw new FjorthException("branch outside definition");
            }
        }
    }

    MachineImpl machine() {
        return machine;
    }

    Dictionary dictionary() {
        return dictionary;
    }

    Definition open() {
        if (definition == null) {
            throw new FjorthException("compilation outside definition");
        }
        return definition;
    }

    Interpreter seal() {
        return new InterpreterImpl(machine, dictionary.seal(), console, true);
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
                throw e.locate(input, tokenStart);
            }
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

    private void run(Word.Colon colon) {
        var body = colon.body();
        var pointer = 0;
        while (pointer < body.size()) {
            pointer = switch (body.get(pointer)) {
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

    private void reset() {
        machine.reset();
        definition = null;
    }

    private String nextToken() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        if (pos == input.length()) {
            return null;
        }
        tokenStart = pos;
        while (pos < input.length() && !Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
        var token = input.substring(tokenStart, pos);
        if (pos < input.length()) {
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

    private static final Result.OK OK = new Result.OK();

    private static BufferedReader libraryReader(String resource) {
        var classLoader = Thread.currentThread().getContextClassLoader();
        var stream = classLoader.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing library resource: " + resource);
        }
        return new BufferedReader(new InputStreamReader(stream, UTF_8));
    }
}
