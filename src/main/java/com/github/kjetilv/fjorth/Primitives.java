package com.github.kjetilv.fjorth;

import module java.base;

final class Primitives {

    @SuppressWarnings("Convert2MethodRef")
    public static final List<Word> WORDS = List.of(
        immediate("ABORT", interpreter -> {
            interpreter.reset();
        }),
        immediate("ABORT\"", interpreter -> {
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
        }),
        primitive(
            "DUP", interpreter -> {
                var machine = interpreter.machine();
                machine.push(machine.peek());
            }
        ),
        primitive("DROP", interpreter -> interpreter.machine().pop()),
        primitive(
            "SWAP", interpreter -> {
                var machine = interpreter.machine();
                var b = machine.pop();
                var a = machine.pop();
                machine.push(b);
                machine.push(a);
            }
        ),
        primitive(
            "OVER", interpreter -> {
                var machine = interpreter.machine();
                var b = machine.pop();
                var a = machine.pop();
                machine.push(a);
                machine.push(b);
                machine.push(a);
            }
        ),
        primitive(
            "ROT", interpreter -> {
                var machine = interpreter.machine();
                var c = machine.pop();
                var b = machine.pop();
                var a = machine.pop();
                machine.push(b);
                machine.push(c);
                machine.push(a);
            }
        ),
        binary("+", (a, b) -> a + b),
        binary("-", (a, b) -> a - b),
        binary("*", (a, b) -> a * b),
        binary(
            "/", (a, b) -> {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a / b;
            }
        ),
        binary(
            "MOD", (a, b) -> {
                if (b == 0) {
                    throw new FjorthException("division by zero");
                }
                return a % b;
            }
        ),
        binary("=", (a, b) -> flag(a == b)),
        binary("<", (a, b) -> flag(a < b)),
        binary(">", (a, b) -> flag(a > b)),
        unary("0=", a -> flag(a == 0)),
        binary("AND", (a, b) -> a & b),
        binary("OR", (a, b) -> a | b),
        binary("XOR", (a, b) -> a ^ b),
        unary("INVERT", a -> ~a),
        primitive(
            ">R", interpreter -> {
                var machine = interpreter.machine();
                machine.pushReturn(machine.pop());
            }
        ),
        primitive(
            "R>", interpreter -> {
                var machine = interpreter.machine();
                machine.push(machine.popReturn());
            }
        ),
        primitive(
            "R@", interpreter -> {
                var machine = interpreter.machine();
                machine.push(machine.peekReturn(0));
            }
        ),
        primitive(
            ".", interpreter -> {
                var machine = interpreter.machine();
                interpreter.print(formatted(machine, machine.pop()) + " ");
            }
        ),
        primitive(
            ".R", interpreter -> {
                var machine = interpreter.machine();
                var width = machine.ipop();
                var text = formatted(machine, machine.pop());
                interpreter.print(" ".repeat(Math.max(0, width - text.length())) + text);
            }
        ),
        primitive(
            ".S", interpreter -> {
                var machine = interpreter.machine();
                var stack = machine.stack();
                var text = new StringBuilder("<").append(stack.length).append("> ");
                for (var value : stack) {
                    text.append(formatted(machine, value)).append(' ');
                }
                interpreter.print(text.toString());
            }
        ),
        primitive(
            "BASE", interpreter -> {
                var machine = interpreter.machine();
                machine.push(machine.baseAddress());
            }
        ),
        primitive(
            "EMIT", interpreter ->
                interpreter.print((char) interpreter.machine().pop())
        ),
        primitive("CR", interpreter -> interpreter.print('\n')),
        immediate(
            ".\"", interpreter -> {
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
        ),
        immediate(
            "S\"", interpreter -> {
                var text = interpreter.readUntil('"');
                var machine = interpreter.machine();
                var address = machine.allot(text.length());
                for (var i = 0; i < text.length(); i++) {
                    machine.store(address + i, text.charAt(i));
                }
                if (machine.compiling()) {
                    interpreter.append(Word.literal(address));
                    interpreter.append(Word.literal(text.length()));
                } else {
                    machine.push(address);
                    machine.push(text.length());
                }
            }
        ),
        primitive(
            "TYPE", interpreter ->
                interpreter.print(poppedString(interpreter.machine()))
        ),
        primitive(
            "EVALUATE", interpreter ->
                interpreter.evaluate(poppedString(interpreter.machine()))
        ),
        immediate("(", interpreter -> interpreter.readUntil(')')),
        immediate("\\", InterpreterImpl::readRestOfLine),
        primitive(":", interpreter -> interpreter.beginDefinition(interpreter.word(":"))),
        immediate(";", InterpreterImpl::endDefinition),
        primitive("IMMEDIATE", InterpreterImpl::makeLatestImmediate),
        immediate("DOES>", interpreter -> interpreter.open().beginTail()),
        primitive(
            "CONSTANT", interpreter -> {
                var name = interpreter.word("CONSTANT");
                var value = interpreter.machine().pop();
                interpreter.define(Word.primitive(name, _ -> interpreter.machine().push(value)));
            }
        ),
        primitive(
            "VARIABLE", interpreter -> {
                var name = interpreter.word("VARIABLE");
                long address = interpreter.machine().allot(1);
                interpreter.define(Word.primitive(name, _ -> interpreter.machine().push(address)));
            }
        ),
        primitive(
            "@", interpreter -> {
                var machine = interpreter.machine();
                var address = machine.pop();
                var value = machine.fetch(address);
                machine.push(value);
            }
        ),
        primitive(
            "!", interpreter -> {
                var machine = interpreter.machine();
                var address = machine.pop();
                machine.store(address, machine.pop());
            }
        ),
        primitive(
            "HERE", interpreter -> {
                var machine = interpreter.machine();
                machine.push(machine.here());
            }
        ),
        primitive(
            "ALLOT", interpreter -> {
                var machine = interpreter.machine();
                machine.allot(machine.ipop());
            }
        ),
        unary("CELLS", cells -> cells),
        noop("CELLS+"),
        primitive(
            "+!", interpreter -> {
                var machine = interpreter.machine();
                var address = machine.pop();
                machine.store(address, machine.fetch(address) + machine.pop());
            }
        ),
        primitive(
            ",", interpreter -> {
                var machine = interpreter.machine();
                machine.store(machine.allot(1), machine.pop());
            }
        ),
        primitive(
            "CREATE", interpreter -> {
                var name = interpreter.word("CREATE");
                long address = interpreter.machine().here();
                interpreter.define(Word.primitive(
                    name,
                    false,
                    _ ->
                        interpreter.machine().push(address)
                ));
            }
        ),
        immediate(
            "IF", interpreter -> {
                var at = interpreter.open().size();
                interpreter.append(Word.zeroBranch(-1));
                interpreter.machine().push(at);
            }
        ),
        immediate(
            "ELSE", interpreter -> {
                var machine = interpreter.machine();
                var ifAt = machine.ipop();
                var elseAt = interpreter.open().size();
                interpreter.append(Word.branch(-1));
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
                interpreter.append(Word.zeroBranch(interpreter.machine().ipop()))
        ),
        immediate(
            "WHILE", interpreter -> {
                var at = interpreter.open().size();
                interpreter.append(Word.zeroBranch(-1));
                interpreter.machine().push(at);
            }
        ),
        immediate(
            "REPEAT", interpreter -> {
                var machine = interpreter.machine();
                var whileAt = machine.ipop();
                var dest = machine.ipop();
                interpreter.append(Word.branch(dest));
                interpreter.open().resolve(whileAt, interpreter.open().size());
            }
        ),
        immediate(
            "DO", interpreter -> {
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
        ),
        immediate(
            "?DO", interpreter -> {
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
                interpreter.append(Word.zeroBranch(-1));
                open.addLeave(skip);
                interpreter.machine().push(open.size());
            }
        ),
        immediate(
            "LOOP",
            interpreter -> closeLoop(
                interpreter,
                primitive(
                    "(loop)", _ ->
                        loopStep(interpreter.machine(), 1)
                )
            )
        ),
        immediate(
            "+LOOP", interpreter -> closeLoop(
                interpreter,
                primitive(
                    "(+loop)", _ -> {
                        var machine1 = interpreter.machine();
                        loopStep(machine1, machine1.pop());
                    }
                )
            )
        ),
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
                interpreter.append(Word.branch(-1));
                interpreter.open().addLeave(at);
            }
        ),
        immediate(
            "EXIT", interpreter ->
                interpreter.append(Word.branch(Integer.MAX_VALUE))
        ),
        immediate(
            "RECURSE", interpreter ->
                interpreter.append(interpreter.open().recurse())
        ),
        primitive(
            "I", interpreter -> {
                var machine = interpreter.machine();
                machine.push(index(machine.peekReturn(0), machine.peekReturn(1)));
            }
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
        primitive(
            "SEE", interpreter -> {
                var name = interpreter.word("SEE");
                var word = interpreter.dictionary().lookup(name)
                    .orElseThrow(() -> new FjorthException(name + " ?"));
                interpreter.print(render(word));
            }
        ),
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
        primitive(
            "C@", interpreter -> {
                var machine = interpreter.machine();
                var address = machine.pop();
                var value = machine.fetch(address);
                machine.push(value);
            }
        ),
        primitive(
            "C!", interpreter -> {
                var machine = interpreter.machine();
                var address = machine.pop();
                var value = machine.pop();
                machine.store(address, 1, value);
            }
        ),
        noop("ALIGN"),
        unary("ALIGNED", cells -> cells)
    );

    private Primitives() {
    }

    private static Word noop(String name) {
        return primitive(
            name, _ -> {
            }
        );
    }

    private static String render(Word word) {
        return switch (word) {
            case Word.Colon(var name, var immediate, var body) -> renderColon(name, immediate, body);
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
        return word instanceof Word.Branch || word instanceof Word.ZeroBranch;
    }

    private static String cell(Word word) {
        return switch (word) {
            case Word.Literal(var value) -> Long.toString(value);
            case Word.Branch(var target) -> target == Integer.MAX_VALUE ? "exit" : "branch -> " + target;
            case Word.ZeroBranch(var target) -> "0branch -> " + target;
            case Word other -> other.name();
        };
    }

    private static void loopStep(MachineImpl machine, long increment) {
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
        interpreter.append(Word.zeroBranch(dest));
        var after = open.size();
        leaves.forEach(site -> open.resolve(site, after));
    }

    private static Word primitive(String name, Word.Effect effect) {
        return Word.primitive(name, false, effect);
    }

    private static Word immediate(String name, Word.Effect effect) {
        return Word.primitive(name, true, effect);
    }

    private static Word unary(String name, LongUnaryOperator op) {
        return primitive(
            name, interpreter -> {
                var machine = interpreter.machine();
                machine.push(op.applyAsLong(machine.pop()));
            }
        );
    }

    private static Word binary(String name, LongBinaryOperator op) {
        return primitive(
            name, interpreter -> {
                var machine = interpreter.machine();
                var b = machine.pop();
                var a = machine.pop();
                machine.push(op.applyAsLong(a, b));
            }
        );
    }

    private static long flag(boolean value) {
        return value ? -1 : 0;
    }

    private static String formatted(MachineImpl m, long value) {
        return Long.toString(value, m.base()).toUpperCase();
    }

    private static String poppedString(MachineImpl m) {
        var length = m.pop();
        var address = m.pop();
        var text = new StringBuilder();
        for (long i = 0; i < length; i++) {
            text.append((char) m.fetch(address + i));
        }
        return text.toString();
    }
}
