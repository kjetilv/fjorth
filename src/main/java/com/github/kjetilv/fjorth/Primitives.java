package com.github.kjetilv.fjorth;

import module java.base;

import static com.github.kjetilv.fjorth.Word.*;

final class Primitives {

    @SuppressWarnings("Convert2MethodRef")
    public static final List<Word> WORDS = List.of(
        immediate(
            "ABORT", interpreter ->
                interpreter.reset()
        ),
        immediate(
            "ABORT\"", interpreter -> {
                var text = interpreter.readUntil('"');
                var machine = interpreter.machine();
                if (machine.compiling()) {
                    interpreter.append(Word.primitive(
                        "(.\")",
                        _ -> interpreter.print(text)
                    ));
                    machine.compiling(false);
                } else {
                    interpreter.print(text);
                }
                interpreter.reset();
            }
        ),
        primitive("DUP", new Dup()),
        primitive("DROP", new Drop()),
        primitive("SWAP", new Swap()),
        primitive("OVER", new Over()),
        primitive("ROT", new Rot()),
        binary("+", new BinaryOp.Add()),
        binary("-", new BinaryOp.Sub()),
        binary("*", new BinaryOp.Mul()),
        binary("/", new BinaryOp.Div()),
        binary("MOD", new BinaryOp.Mod()),
        binary("=", new BinaryOp.Eql()),
        binary("<", new BinaryOp.LTh()),
        binary(">", new BinaryOp.GTh()),
        unary("0=", new UnaryOp.Eq0()),
        binary("AND", new BinaryOp.And()),
        binary("OR", new BinaryOp.Or()),
        binary("XOR", new BinaryOp.Xor()),
        unary("INVERT", new UnaryOp.Invert()),
        primitive(">R", new PushReturn()),
        primitive("R>", new PopReturn()),
        primitive("R@", new PeekReturn()),
        primitive(".", new Dot()),
        primitive(".R", new DotR()),
        primitive(".S", new DotS()),
        primitive("BASE", new Base()),
        primitive("EMIT", new Emit()),
        primitive("CR", interpreter -> interpreter.print('\n')),
        immediate(".\"", new DotQuote()),
        immediate("S\"", new SQuote()),
        primitive("TYPE", new Type()),
        primitive("EVALUATE", new Evaluate()),
        immediate("(", new ReadToRightPar()),
        immediate("\\", new ReadRestOfLine()),
        primitive(":", new BeginDefinition()),
        immediate(";", new EndDefinition()),
        primitive("IMMEDIATE", InterpreterImpl::makeLatestImmediate),
        immediate("DOES>", interpreter -> interpreter.open().beginTail()),
        primitive("CONSTANT", new Constant()),
        primitive("VARIABLE", new Variable()),
        primitive("@", new Fetch()),
        primitive("!", new Store()),
        primitive("HERE", new Here()),
        primitive("ALLOT", new Allot()),
        unary("CELLS", new UnaryOp.Identity()),
        noop("CELLS+"),
        primitive("+!", new AddStore()),
        primitive(",", new Comma()),
        primitive("CREATE", new Create()),
        immediate(
            "IF", interpreter -> {
                var at = interpreter.open().size();
                interpreter.append(zeroBranch(-1));
                interpreter.machine().push(at);
            }
        ),
        immediate(
            "ELSE", interpreter -> {
                var machine = interpreter.machine();
                var ifAt = machine.ipop();
                var elseAt = interpreter.open().size();
                interpreter.append(branch(-1));
                interpreter.open().resolve(ifAt, interpreter.open().size());
                machine.push(elseAt);
            }
        ),
        immediate(
            "THEN", interpreter -> {
                var at = interpreter.machine().ipop();
                interpreter.open().resolve(at, interpreter.open().size());
            }
        ),
        immediate(
            "BEGIN", interpreter ->
                interpreter.machine().push(interpreter.open().size())
        ),
        immediate(
            "UNTIL", interpreter ->
                interpreter.append(zeroBranch(interpreter.machine().ipop()))
        ),
        immediate(
            "WHILE", interpreter -> {
                var at = interpreter.open().size();
                interpreter.append(zeroBranch(-1));
                interpreter.machine().push(at);
            }
        ),
        immediate(
            "REPEAT", interpreter -> {
                var machine = interpreter.machine();
                var whileAt = machine.ipop();
                var dest = machine.ipop();
                interpreter.append(branch(dest));
                interpreter.open().resolve(whileAt, interpreter.open().size());
            }
        ),
        immediate("DO", new Do()),
        immediate("?DO", new QDo()),
        immediate("LOOP", new Loop()),
        immediate("+LOOP", new PlusLoop()),
        immediate(
            "LEAVE", interpreter -> {
                var machine = interpreter.machine();
                interpreter.append(primitive(
                    "(unloop)", _ -> {
                        machine.popReturn();
                        machine.popReturn();
                    }
                ));
                var at = interpreter.open().size();
                interpreter.append(branch(-1));
                interpreter.open().addLeave(at);
            }
        ),
        immediate(
            "EXIT", interpreter ->
                interpreter.append(branch(Integer.MAX_VALUE))
        ),
        immediate(
            "RECURSE", interpreter ->
                interpreter.append(interpreter.open().recurse())
        ),
        primitive(
            "I", new I()
        ),
        primitive(
            "J", interpreter -> {
                var machine = interpreter.machine();
                machine.push(index(machine.peekReturn(2), machine.peekReturn(3)));
            }
        ),
        primitive(
            "WORDS", interpreter -> {
                interpreter.print(interpreter.dictionary().words()
                    .map(Word::name)
                    .distinct()
                    .collect(Collectors.joining(" ")));
                interpreter.print('\n');
            }
        ),
        primitive("SEE", new See()),
        primitive(
            "FILL", interpreter -> {
                var machine = interpreter.machine();
                var c = machine.pop();
                var count = machine.pop();
                var address = machine.pop();
                machine.store(address, count, c);
            }
        ),
        primitive(
            "ERASE", interpreter -> {
                var machine = interpreter.machine();
                var count = machine.pop();
                var address = machine.pop();
                machine.store(address, count, (char) 0);
            }
        ),
        primitive("C@", new FetchChar()),
        primitive("C!", new StoreChar()),
        noop("ALIGN"),
        unary("ALIGNED", cells -> cells)
    );

    private Primitives() {
    }

    private static Word noop(String name) {
        return primitive(name, new Noop());
    }

    private static String render(Word word) {
        return switch (word) {
            case Colon(var name, var immediate, var body) -> renderColon(name, immediate, body);
            case Word other -> other.name() + " ( primitive )\n";
        };
    }

    private static String renderColon(String name, boolean immediate, List<Word> body) {
        var suffix = immediate ? " IMMEDIATE\n" : "\n";
        if (body.stream().anyMatch(Primitives::isBranch)) {
            var text = new StringBuilder(": ").append(name).append('\n');
            for (var i = 0; i < body.size(); i++) {
                text.append(String.format("%4d: %s\n", i, cell(body.get(i))));
            }
            return text.append(';').append(suffix).toString();
        }
        return body.isEmpty()
            ? ": " + name + " ;" + suffix
            : body.stream()
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
        var machine = interpreter.machine();
        var open = interpreter.open();
        var leaves = open.endLoop();
        var dest = machine.ipop();
        interpreter.append(runtime);
        interpreter.append(zeroBranch(dest));
        var after = open.size();
        leaves.forEach(site -> open.resolve(site, after));
    }

    private static Word primitive(String name, Effect effect) {
        return Word.primitive(name, false, effect);
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

    static final class Noop implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
        }
    }

    static final class EndDefinition implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.endDefinition();
        }
    }

    static final class BeginDefinition implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.beginDefinition(interpreter.word(":"));
        }
    }

    static final class Base implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.baseAddress());
        }
    }

    static final class Dot implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.print(formatted(machine, machine.pop()) + " ");
        }
    }

    static final class Dup implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.peek());
        }
    }

    static final class Drop implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.machine().pop();
        }
    }

    static final class Swap implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var b = machine.pop();
            var a = machine.pop();
            machine.push(b);
            machine.push(a);
        }
    }

    static final class Over implements Effect {

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

    static final class SQuote implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var text = interpreter.readUntil('"');
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

    static final class Rot implements Effect {

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

    static final class BinaryOp implements Effect {

        private final LongBinaryOperator op;

        private BinaryOp(LongBinaryOperator op) {
            this.op = op;
        }

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var b = machine.pop();
            var a = machine.pop();
            var value = switch (op) {
                case Add add -> add.applyAsLong(a, b);
                case Sub sub -> sub.applyAsLong(a, b);
                case Mul mul -> mul.applyAsLong(a, b);
                case Div div -> div.applyAsLong(a, b);
                case Mod mod -> mod.applyAsLong(a, b);
                case Eql eql -> eql.applyAsLong(a, b);
                case GTh gTh -> gTh.applyAsLong(a, b);
                case LTh lTh -> lTh.applyAsLong(a, b);
                case And and -> and.applyAsLong(a, b);
                case Or or -> or.applyAsLong(a, b);
                case Xor xor -> xor.applyAsLong(a, b);
                default -> op.applyAsLong(a, b);
            };
            machine.push(value);
        }

        private static final class Add implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a + b;
            }
        }

        private static class Sub implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a - b;
            }
        }

        private static class Mul implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a * b;
            }
        }

        private static class Div implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a / b;
            }
        }

        private static class Mod implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a % b;
            }
        }

        private static class Eql implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a == b);
            }
        }

        private static class LTh implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a < b);
            }
        }

        private static class GTh implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return flag(a > b);
            }
        }

        private static class And implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a & b;
            }
        }

        private static class Or implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a | b;
            }
        }

        private static class Xor implements LongBinaryOperator {

            @Override
            public long applyAsLong(long a, long b) {
                return a ^ b;
            }
        }
    }

    static final class PushReturn implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.pushReturn(machine.pop());
        }
    }

    static final class PopReturn implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.popReturn());
        }
    }

    static final class PeekReturn implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.peekReturn(0));
        }
    }

    static final class Emit implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.print((char) interpreter.machine().pop());
        }
    }

    static final class UnaryOp implements Effect {

        private final LongUnaryOperator op;

        private UnaryOp(LongUnaryOperator op) {
            this.op = op;
        }

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var a = machine.pop();
            var value = switch (op) {
                case Eq0 eq0 -> eq0.applyAsLong(a);
                case Invert invert -> invert.applyAsLong(a);
                case Identity identity -> identity.applyAsLong(a);
                default -> op.applyAsLong(a);
            };
            machine.push(value);
        }

        static class Identity implements LongUnaryOperator {

            @Override
            public long applyAsLong(long cells) {
                return cells;
            }
        }

        private static class Eq0 implements LongUnaryOperator {

            @Override
            public long applyAsLong(long a) {
                return flag(a == 0);
            }
        }

        private static class Invert implements LongUnaryOperator {

            @Override
            public long applyAsLong(long a) {
                return ~a;
            }
        }
    }

    static final class ReadRestOfLine implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.readRestOfLine();
        }
    }

    static final class ReadToRightPar implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.readUntil(')');
        }
    }

    static final class Constant implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("CONSTANT");
            var value = interpreter.machine().pop();
            interpreter.define(Word.primitive(name, _ -> interpreter.machine().push(value)));
        }
    }

    static final class Variable implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("VARIABLE");
            long address = interpreter.machine().allot(1);
            interpreter.define(Word.primitive(name, _ -> interpreter.machine().push(address)));
        }
    }

    static final class DotQuote implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var text = interpreter.readUntil('"');
            if (interpreter.machine().compiling()) {
                interpreter.append(Word.primitive(
                    "(.\")",
                    _ -> interpreter.print(text)
                ));
            } else {
                interpreter.print(text);
            }
        }
    }

    static final class Fetch implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.fetch(address);
            machine.push(value);
        }
    }

    static final class Store implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            machine.store(address, machine.pop());
        }
    }

    static final class Here implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(machine.here());
        }
    }

    static final class Allot implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.allot(machine.ipop());
        }
    }

    static final class AddStore implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            machine.store(address, machine.fetch(address) + machine.pop());
        }
    }

    static final class Comma implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.store(machine.allot(1), machine.pop());
        }
    }

    static final class DotR implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var width = machine.ipop();
            var text = formatted(machine, machine.pop());
            interpreter.print(" ".repeat(Math.max(0, width - text.length())) + text);
        }
    }

    static final class DotS implements Effect {

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

    static final class Type implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.print(poppedString(interpreter.machine()));
        }
    }

    static final class Evaluate implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.evaluate(poppedString(interpreter.machine()));
        }
    }

    static final class Create implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("CREATE");
            long address = interpreter.machine().here();
            interpreter.define(Word.primitive(
                name,
                false,
                _ ->
                    interpreter.machine().push(address)
            ));
        }
    }

    static final class Loop implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            closeLoop(
                interpreter,
                primitive(
                    "(loop)", _ ->
                        loopStep(interpreter.machine(), 1)
                )
            );
        }
    }

    static final class PlusLoop implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            closeLoop(
                interpreter,
                primitive(
                    "(+loop)", _ -> {
                        var machine1 = interpreter.machine();
                        loopStep(machine1, machine1.pop());
                    }
                )
            );
        }
    }

    static final class I implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            machine.push(index(machine.peekReturn(0), machine.peekReturn(1)));
        }
    }

    static final class StoreChar implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.pop();
            machine.store(address, 1, value);
        }
    }

    static final class FetchChar implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            var address = machine.pop();
            var value = machine.fetch(address);
            machine.push(value);
        }
    }

    static final class Do implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.append(primitive(
                "(do)", _ -> {
                    var index = machine.pop();
                    var limit = machine.pop();
                    machine.pushReturn(limit);
                    machine.pushReturn(slot(index, limit));
                }
            ));
            interpreter.open().beginLoop();
            interpreter.machine().push(interpreter.open().size());
        }
    }

    static final class QDo implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var machine = interpreter.machine();
            interpreter.append(primitive(
                "(?do)", _ -> {
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
            ));
            var open = interpreter.open();
            open.beginLoop();
            var skip = open.size();
            interpreter.append(zeroBranch(-1));
            open.addLeave(skip);
            interpreter.machine().push(open.size());
        }
    }

    static final class See implements Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            var name = interpreter.word("SEE");
            var word = interpreter.dictionary().lookup(name)
                .orElseThrow(() -> new FjorthException(name + " ?"));
            interpreter.print(render(word));
        }
    }
}
