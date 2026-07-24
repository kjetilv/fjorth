package com.github.kjetilv.fjorth;

import module java.base;

import com.github.kjetilv.fjorth.Interpreter.Result.Failed;

import static com.github.kjetilv.fjorth.Primitives.*;

final class InterpreterImpl implements Interpreter {

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

    private String input = "";

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

    InterpreterImpl loadLibrary(String resource) {
        if (load(resource) instanceof Result.Failed(var message)) {
            throw new IllegalStateException("Failed to execute library: " + message);
        }
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
            dictionary.insert(word);
        }
    }

    void makeLatestImmediate() {
        var latest = dictionary.latest()
            .orElseThrow(() -> new FjorthException("IMMEDIATE: empty dictionary"));
        if (!(latest instanceof Word.Colon colon)) {
            throw new FjorthException("IMMEDIATE: not a colon definition: " + latest.name());
        }
        define(colon.asImmediate());
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

    void execute(Word word) {
        switch (word) {
            case Word.Primitive primitive -> effect(primitive.effect());
            case Word.Colon colon -> executeAll(colon.body());
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

    Definition open() {
        if (definition == null) {
            throw new FjorthException("compilation outside definition");
        }
        return definition;
    }

    Interpreter seal() {
        return new InterpreterImpl(machine, dictionary.seal(), console, true);
    }

    void reset() {
        reset(null);
    }

    void reset(Dictionary dictionary) {
        this.machine.reset();
        this.input = "";
        this.pos = 0;
        this.tokenStart = 0;
        this.definition = null;
        if (dictionary != null) {
            this.dictionary = dictionary;
        }
    }

    private void effect(Word.Effect effect) {
        switch (effect) {
            case Abort abort -> abort.apply(this);
            case AbortQuote abortQuote -> abortQuote.apply(this);
            case AddStore addStore -> addStore.apply(this);
            case Allot allot -> allot.apply(this);
            case Base base -> base.apply(this);
            case Begin begin -> begin.apply(this);
            case BeginDefinition beginDefinition -> beginDefinition.apply(this);
            case BinaryOp binaryOp -> binaryOp.apply(this);
            case Comma comma -> comma.apply(this);
            case Constant constant -> constant.apply(this);
            case Create create -> create.apply(this);
            case Definition.PrimitiveDoes primitiveDoes -> primitiveDoes.apply(this);
            case Definition.PrimitiveDoes.InnerDoes innerDoes -> innerDoes.apply(this);
            case Definition.Recurse recurse -> recurse.apply(this);
            case Do do_ -> do_.apply(this);
            case Dot dot -> dot.apply(this);
            case DotQuote dotQuote -> dotQuote.apply(this);
            case DotR dotR -> dotR.apply(this);
            case DotS dotS -> dotS.apply(this);
            case Drop drop -> drop.apply(this);
            case Dup dup -> dup.apply(this);
            case Else else_ -> else_.apply(this);
            case Emit emit -> emit.apply(this);
            case EndDefinition endDefinition -> endDefinition.apply(this);
            case Erase erase -> erase.apply(this);
            case Evaluate evaluate -> evaluate.apply(this);
            case Exit exit -> exit.apply(this);
            case Fetch fetch -> fetch.apply(this);
            case FetchChar fetchChar -> fetchChar.apply(this);
            case Fill fill -> fill.apply(this);
            case Here here -> here.apply(this);
            case I i -> i.apply(this);
            case If if_ -> if_.apply(this);
            case ImmediateDoes immediateDoes -> immediateDoes.apply(this);
            case ImmediateRecurse immediateRecurse -> immediateRecurse.apply(this);
            case InnerDo innerDo -> innerDo.apply(this);
            case J j -> j.apply(this);
            case Leave leave -> leave.apply(this);
            case Leave.PopReturn2 popReturn2 -> popReturn2.apply(this);
            case Loop loop -> loop.apply(this);
            case Loop.InnerLoop innerLoop -> innerLoop.apply(this);
            case MachinePush machinePush -> machinePush.apply(this);
            case MakeLatestImmediate makeLatestImmediate -> makeLatestImmediate.apply(this);
            case Noop noop -> noop.apply(this);
            case Over over -> over.apply(this);
            case PeekReturn peekReturn -> peekReturn.apply(this);
            case PlusLoop plusLoop -> plusLoop.apply(this);
            case PlusLoop.InnerPlusLoop innerPlusLoop -> innerPlusLoop.apply(this);
            case PopReturn popReturn -> popReturn.apply(this);
            case Print print -> print.apply(this);
            case PushReturn pushReturn -> pushReturn.apply(this);
            case QDo qDo -> qDo.apply(this);
            case QDo.InnerDo innerDo -> innerDo.apply(this);
            case ReadRestOfLine readRestOfLine -> readRestOfLine.apply(this);
            case ReadToRightPar readToRightPar -> readToRightPar.apply(this);
            case Repeat repeat -> repeat.apply(this);
            case Rot rot -> rot.apply(this);
            case SQuote sQuote -> sQuote.apply(this);
            case See see -> see.apply(this);
            case Store store -> store.apply(this);
            case StoreChar storeChar -> storeChar.apply(this);
            case Swap swap -> swap.apply(this);
            case Then then -> then.apply(this);
            case Type type -> type.apply(this);
            case UnaryOp unaryOp -> unaryOp.apply(this);
            case Until until -> until.apply(this);
            case Variable variable -> variable.apply(this);
            case While while_ -> while_.apply(this);
            case Words words -> words.apply(this);
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
                throw e.locate(input, tokenStart);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process tokens", e);
            }
        }
    }

    private Result load(String resource) {
        var stream =
            Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("missing library resource: " + resource);
        }
        try (
            var bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
        ) {
            return bufferedReader.lines()
                .map(this::interpret)
                .filter(Failed.class::isInstance)
                .findFirst()
                .orElse(OK);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + resource, e);
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

    private void executeAll(List<Word> body) {
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

    private static void outsideDefinition(int target) {
        throw new FjorthException("branch outside definition: " + target);
    }

}
