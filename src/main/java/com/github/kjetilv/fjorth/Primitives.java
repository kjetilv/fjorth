package com.github.kjetilv.fjorth;

import module java.base;

import static com.github.kjetilv.fjorth.Word.*;

@SuppressWarnings("DuplicatedCode")
final class Primitives {

    private Primitives() {
    }

    static final Word[] WORDS = {
        binary("*", new BinaryOp.Mul()),
        binary("+", new BinaryOp.Add()),
        binary("-", new BinaryOp.Sub()),
        binary("/", new BinaryOp.Div()),
        binary("<", new BinaryOp.LTh()),
        binary("=", new BinaryOp.Eql()),
        binary(">", new BinaryOp.GTh()),
        binary("AND", new BinaryOp.And()),
        binary("MOD", new BinaryOp.Mod()),
        binary("OR", new BinaryOp.Or()),
        binary("XOR", new BinaryOp.Xor()),
        binary("LSHIFT", new BinaryOp.LSh()),
        binary("RSHIFT", new BinaryOp.RSh()),
        immediate("(", new ReadToRightPar()),
        immediate("+LOOP", new PlusLoop()),
        immediate(".\"", new DotQuote()),
        immediate(";", new EndDefinition()),
        immediate("?DO", new QDo()),
        immediate("ABORT", new Abort()),
        immediate("ABORT\"", new AbortQuote()),
        immediate("BEGIN", new Begin()),
        immediate("DO", new Do()),
        immediate("DOES>", new ImmediateDoes()),
        immediate("ELSE", new Else()),
        immediate("EXIT", new Exit()),
        immediate("IF", new If()),
        immediate("LEAVE", new Leave()),
        immediate("LOOP", new Loop()),
        immediate("RECURSE", new ImmediateRecurse()),
        immediate("REPEAT", new Repeat()),
        immediate("S\"", new SQuote()),
        immediate("THEN", new Then()),
        immediate("UNTIL", new Until()),
        immediate("WHILE", new While()),
        immediate("\\", new ReadRestOfLine()),
        primitive("ALIGN", new Noop()),
        primitive("BL", new Bl()),
        primitive("CELLS+", new Noop()),
        primitive("!", new Store()),
        primitive("+!", new AddStore()),
        primitive(",", new Comma()),
        primitive(".", new Dot()),
        primitive(".R", new DotR()),
        primitive(".S", new DotS()),
        primitive(":", new BeginDefinition()),
        primitive(">R", new PushReturn()),
        primitive("@", new Fetch()),
        primitive("ALLOT", new Allot()),
        primitive("BASE", new Base()),
        primitive("C!", new StoreChar()),
        primitive("C@", new FetchChar()),
        primitive("CONSTANT", new Constant()),
        primitive("CR", new Print("\n")),
        primitive("CREATE", new Create()),
        primitive("DROP", new Drop()),
        primitive("DUP", new Dup()),
        primitive("EMIT", new Emit()),
        primitive("ERASE", new Erase()),
        primitive("EVALUATE", new Evaluate()),
        primitive("FILL", new Fill()),
        primitive("HERE", new Here()),
        primitive("I", new I()),
        primitive("IMMEDIATE", new MakeLatestImmediate()),
        primitive("J", new J()),
        primitive("OVER", new Over()),
        primitive("R>", new PopReturn()),
        primitive("R@", new PeekReturn()),
        primitive("ROT", new Rot()),
        primitive("SEE", new See()),
        primitive("STATE", new State()),
        primitive("SWAP", new Swap()),
        primitive("TO", new To()),
        primitive("TYPE", new Type()),
        primitive("VALUE", new Value()),
        primitive("VARIABLE", new Variable()),
        primitive("WORDS", new Words()),
        unary("0=", new UnaryOp.Eq0()),
        unary("ALIGNED", new UnaryOp.Identity()),
        unary("CELLS", new UnaryOp.Identity()),
        unary("INVERT", new UnaryOp.Invert())
    };

    private static String render(Word word) {
        return switch (word) {
            case Colon(var name, var immediate, var body) -> renderColon(name, immediate, body);
            case Word other -> other.name() + " ( primitive )\n";
        };
    }

    private static String renderColon(String name, boolean immediate, Word[] body) {
        var suffix = immediate ? " IMMEDIATE\n" : "\n";
        if (Arrays.stream(body).anyMatch(Primitives::isBranch)) {
            var text = new StringBuilder(": ").append(name).append('\n');
            for (var i = 0; i < body.length; i++) {
                text.append(String.format("%4d: %s\n", i, cell(body[i])));
            }
            return text.append(';').append(suffix).toString();
        }
        return body.length == 0
            ? ": " + name + " ;" + suffix
            : Arrays.stream(body)
                .map(Primitives::cell)
                .collect(Collectors.joining(" ", ": " + name + " ", " ;" + suffix));
    }

    private static boolean isBranch(Word word) {
        return word instanceof Branch || word instanceof ZeroBranch;
    }

    private static String cell(Word word) {
        return switch (word) {
            case Literal(var value) -> Long.toString(value);
            case Branch(var target) -> target == Integer.MAX_VALUE ? "exit" : "branch -> " + target;
            case ZeroBranch(var target) -> "0branch -> " + target;
            case Word other -> other.name();
        };
    }

    private static void loopStep(MachineApi machine, long increment) {
        var slot = machine.popReturn();
        var next = slot + increment;
        var crossed = ((slot ^ next) & (increment ^ next)) < 0;
        if (crossed) {
            machine.popReturn();
            machine.push(-1);
        } else {
            machine.pushReturn(next);
            machine.push(0);
        }
    }

    /// Loop counters live on the return stack biased: `slot = index - limit + MIN_VALUE`.
    /// The ANS limit-1/limit boundary then sits exactly at `MAX_VALUE`/`MIN_VALUE`, so:
    ///
    /// > _index crossed the boundary_ == _slot + increment overflowed_
    ///
    /// Correct even when the index itself wraps the 64-bit range. `I`/`J` reconstruct via [index][#index(long, long)].
    private static long slot(long index, long limit) {
        return index - limit + Long.MIN_VALUE;
    }

    private static long index(long slot, long limit) {
        return slot + limit + Long.MIN_VALUE;
    }

    private static void closeLoop(InterpreterImpl interpreter, Word runtime) {
        interpreter.openDefinitionEndLoop();
        var dest = interpreter.machine().ipop();
        interpreter.append(runtime);
        interpreter.append(zeroBranch(dest));
        interpreter.openDefinitionCloseLoop();
    }

    private static Word primitive(String name, Effect effect) {
        return Word.primitive(name, effect);
    }

    private static Word primitive(String name, Runnable effect) {
        return Word.primitive(name, effect);
    }

    private static Word immediate(String name, Effect effect) {
        return Word.primitive(name, true, effect);
    }

    private static Word unary(String name, LongUnaryOperator op) {
        return primitive(name, new UnaryOp(op));
    }

    private static Word binary(String name, LongBinaryOperator op) {
        return primitive(name, new BinaryOp(op));
    }

    private static long flag(boolean value) {
        return value ? -1 : 0;
    }

    private static String formatted(MachineApi m, long value) {
        return Long.toString(value, m.base()).toUpperCase();
    }

    private static String poppedString(MachineApi m) {
        var length = m.pop();
        var address = m.pop();
        var text = new StringBuilder();
        for (long i = 0; i < length; i++) {
            text.append((char) m.fetch(address + i));
        }
        return text.toString();
    }

    record Noop() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
        }
    }

    record EndDefinition() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.endDefinition();
        }
    }

    record BeginDefinition() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.beginDefinition(interpreter.word(":"));
        }
    }

    record Base() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.baseAddress());
        }
    }

    record Dot() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.print(formatted(machine, machine.pop()) + " ");
        }
    }

    record Dup() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.peek());
        }
    }

    record Drop() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.machine().pop();
        }
    }

    record Swap() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var b = machine.pop();
            var a = machine.pop();
            machine.push(b);
            machine.push(a);
        }
    }

    record Over() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var b = machine.pop();
            var a = machine.pop();
            machine.push(a);
            machine.push(b);
            machine.push(a);
        }
    }

    record SQuote() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var text = interpreter.readString();
            var machine = interpreter.machine();
            var address = machine.allot(text.length());
            for (var i = 0; i < text.length(); i++) {
                machine.store(address + i, text.charAt(i));
            }
            if (machine.compiling()) {
                interpreter.append(literal(address));
                interpreter.append(literal(text.length()));
            } else {
                machine.push(address);
                machine.push(text.length());
            }
        }

    }

    record Rot() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var c = machine.pop();
            var b = machine.pop();
            var a = machine.pop();
            machine.push(b);
            machine.push(c);
            machine.push(a);
        }

    }

    record BinaryOp(LongBinaryOperator op) implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var b = machine.pop();
            var a = machine.pop();
            var value = op.applyAsLong(a, b);
            machine.push(value);
        }

        private record Add() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a + b;
            }
        }

        private record Sub() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a - b;
            }
        }

        private record Mul() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a * b;
            }
        }

        private record Div() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a / b;
            }
        }

        private record Mod() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a % b;
            }
        }

        private record Eql() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a == b);
            }
        }

        private record LTh() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a < b);
            }
        }

        private record GTh() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a > b);
            }
        }

        private record LSh() implements LongBinaryOperator {
            @Override
            public long applyAsLong(long x, long a) {
                return x << a;
            }
        }

        private record RSh() implements LongBinaryOperator {
            @Override
            public long applyAsLong(long x, long a) {
                return x >> a;
            }
        }

        private record And() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a & b;
            }
        }

        private record Or() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a | b;
            }
        }

        private record Xor() implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a ^ b;
            }
        }
    }

    private record PushReturn() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.pushReturn(machine.pop());
        }
    }

    private record PopReturn() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.popReturn());
        }
    }

    private record PeekReturn() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.peekReturn(0));
        }
    }

    private record Emit() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.print((char) machine.pop());
        }
    }

    private record UnaryOp(LongUnaryOperator op) implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var a = machine.pop();
            var value = op.applyAsLong(a);
            machine.push(value);
        }

        record Identity() implements LongUnaryOperator {

            @Override
            public long applyAsLong(long cells) {
                return cells;
            }
        }

        record Eq0() implements LongUnaryOperator {

            @Override
            public long applyAsLong(long a) {
                return flag(a == 0);
            }
        }

        record Invert() implements LongUnaryOperator {

            @Override
            public long applyAsLong(long a) {
                return ~a;
            }
        }
    }

    private record ReadRestOfLine() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.readRestOfLine();
        }
    }

    private record ReadToRightPar() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.readUntil(')');
        }
    }

    private record Constant() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var name = interpreter.word("CONSTANT");
            var value = machine.pop();
            interpreter.define(Word.primitive(name, new MachinePush(machine, value)));
        }
    }

    private record Variable() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var name = interpreter.word("VARIABLE");
            long address = machine.allot(1);
            interpreter.define(Word.primitive(name, new MachinePush(machine, address)));
        }
    }

    private record DotQuote() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var text = interpreter.readString();
            if (interpreter.machine().compiling()) {
                interpreter.append(Word.primitive("(.\")", new Print(text)));
            } else {
                interpreter.print(text);
            }
        }
    }

    private record Fetch() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.fetch(address);
            machine.push(value);
        }
    }

    private record Store() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            machine.store(address, machine.pop());
        }
    }

    private record Here() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.here());
        }
    }

    private record Allot() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.allot(machine.ipop());
        }
    }

    private record AddStore() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            machine.store(address, machine.fetch(address) + machine.pop());
        }
    }

    private record Comma() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.store(machine.allot(1), machine.pop());
        }
    }

    private record DotR() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var width = machine.ipop();
            var text = formatted(machine, machine.pop());
            interpreter.print(" ".repeat(Math.max(0, width - text.length())) + text);
        }
    }

    private record DotS() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var stack = machine.stack();
            var text = new StringBuilder("<").append(stack.length).append("> ");
            for (var value : stack) {
                text.append(formatted(machine, value)).append(' ');
            }
            interpreter.print(text.toString());
        }
    }

    private record Type() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.print(poppedString(interpreter.machine()));
        }
    }

    private record Evaluate() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.evaluate(poppedString(interpreter.machine()));
        }
    }

    private record Create() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("CREATE");
            var machine = interpreter.machine();
            long address = interpreter.machine().here();
            interpreter.define(Word.primitive(name, new MachinePush(machine, address)));
        }
    }

    private record Loop() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            closeLoop(
                interpreter,
                primitive(
                    "(loop)", new InnerLoop(machine)
                )
            );
        }

        private record InnerLoop(MachineApi machine) implements Effect {

            @Override
            public void apply(InterpreterImpl interpreter) {
                loopStep(machine, 1);
            }
        }
    }

    private record PlusLoop() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            closeLoop(
                interpreter,
                primitive(
                    "(+loop)", new InnerPlusLoop(machine)
                )
            );
        }

        private record InnerPlusLoop(MachineApi machine) implements Effect {

            @Override
            public void apply(InterpreterImpl i) {
                loopStep(machine, machine.pop());
            }
        }
    }

    private record I() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var slot = machine.peekReturn();
            var limit = machine.peekReturn(1);
            var index = index(slot, limit);
            machine.push(index);
        }
    }

    private record StoreChar() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.pop();
            machine.store(address, 1, value);
        }
    }

    private record FetchChar() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.fetch(address);
            machine.push(value);
        }
    }

    private record Do() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.append(primitive("(do)", new InnerDo(machine)));
            interpreter.openDefinitionBeginLoop();
            interpreter.machine().push(interpreter.openDefinitionSize());
        }
    }

    private record QDo() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.append(primitive("(?do)", new InnerDo(machine)));
            interpreter.openDefinitionBeginLoop();
            var skip = interpreter.openDefinitionSize();
            interpreter.append(zeroBranch(-1));
            interpreter.openDefinition().addLeave(skip);
            interpreter.machine().push(skip + 1);
        }

        private record InnerDo(MachineApi machine) implements Effect {

            @Override
            public void apply(InterpreterImpl i) {
                var index = machine.pop();
                var limit = machine.pop();
                if (limit == index) {
                    machine.push(0);
                } else {
                    machine.pushReturn(limit);
                    machine.pushReturn(slot(index, limit));
                    machine.push(-1);
                }
            }
        }
    }

    private record See() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("SEE");
            var word = interpreter.dictionary().lookup(name);
            if (word == null) {
                throw new FjorthException(name + " ?");
            }
            interpreter.print(render(word));
        }
    }

    private record ImmediateDoes() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.openDefinitionBeginTail();
        }
    }

    private record If() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            int at = interpreter.openDefinitionSize();
            interpreter.append(zeroBranch(-1));
            interpreter.machine().push(at);
        }
    }

    private record Else() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var ifAt = machine.ipop();
            int elseAt = interpreter.openDefinitionSize();
            interpreter.append(branch(-1));
            interpreter.openDefinition().redirectAt(ifAt, elseAt + 1);
            machine.push(elseAt);
        }
    }

    private record Then() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var at = interpreter.machine().ipop();
            int size = interpreter.openDefinitionSize();
            interpreter.openDefinition().redirectAt(at, size);
        }
    }

    private record Begin() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            int size = interpreter.openDefinitionSize();
            interpreter.machine().push(size);
        }
    }

    private record Until() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.append(zeroBranch(interpreter.machine().ipop()));
        }
    }

    private record While() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            int at = interpreter.openDefinitionSize();
            interpreter.append(zeroBranch(-1));
            interpreter.machine().push(at);
        }
    }

    private record Repeat() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var whileAt = machine.ipop();
            var dest = machine.ipop();
            interpreter.append(branch(dest));
            int size = interpreter.openDefinitionSize();
            interpreter.openDefinition().redirectAt(whileAt, size);
        }
    }

    private record Leave() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.append(primitive("(unloop)", new PopReturn2(machine)));
            int at = interpreter.openDefinitionSize();
            interpreter.append(branch(-1));
            interpreter.openDefinition().addLeave(at);
        }

        private record PopReturn2(MachineApi machine) implements Effect {

            @Override
            public void apply(InterpreterImpl interpreter) {
                machine.popReturn();
                machine.popReturn();
            }
        }
    }

    private record ImmediateRecurse() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.openDefinitionRecurse();
        }
    }

    private record Exit() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.append(branch(Integer.MAX_VALUE));
        }
    }

    private record J() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var slot = machine.peekReturn(2);
            var limit = machine.peekReturn(3);
            var index = index(slot, limit);
            machine.push(index);
        }
    }

    private record Erase() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var count = machine.pop();
            var address = machine.pop();
            machine.store(address, count, (char) 0);
        }
    }

    private record Fill() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var c = machine.pop();
            var count = machine.pop();
            var address = machine.pop();
            machine.store(address, count, c);
        }
    }

    private record Words() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.print(interpreter.dictionary().words()
                .distinct()
                .collect(Collectors.joining(" ")));
            interpreter.print('\n');
        }
    }

    private record AbortQuote() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var text = interpreter.readString();
            var machine = interpreter.machine();
            if (machine.compiling()) {
                interpreter.append(Word.primitive("(.\")", new Print(text)));
                machine.compiling(false);
            } else {
                interpreter.print(text);
            }
            interpreter.reset();
        }

    }

    private record Abort() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.reset();
        }
    }

    private record MachinePush(MachineApi machine, long value) implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            machine.push(value);
        }
    }

    private record InnerDo(MachineApi machine) implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var index = machine.pop();
            var limit = machine.pop();
            machine.pushReturn(limit);
            machine.pushReturn(slot(index, limit));
        }
    }

    private record Print(String text) implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.print(text);
        }
    }

    private record MakeLatestImmediate() implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.makeLatestImmediate();
        }
    }

    private static class Value implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var a = machine.pop();
            var name = interpreter.readString();
            interpreter.define(
                value(
                    name,
                    () -> machine.push(a)
                ));
        }
    }

    private static class To implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            if (machine.compiling()) {
                interpreter.append(primitive("(to)", new To()));
                var name = interpreter.word("TO");
                interpreter.append(primitive(
                    name, () -> {
                    }
                ));
            } else {
                var a = machine.pop();
                var name = interpreter.word("TO");
                Word word = interpreter.dictionary().lookup(name);
                if (word == null) {
                    throw new FjorthException(name + " ?");
                }
                if (!(word instanceof Word.Value)) {
                    throw new FjorthException(name + " ?");
                }
                interpreter.define(value(name, () -> machine.push(a)));
            }
        }
    }

    private static class Bl implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.machine().push(' ');
        }
    }

    private static class State implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.compiling());
        }
    }
}
