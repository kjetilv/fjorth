package com.github.kjetilv.fjorth;

import module java.base;

final class Primitives {

    static Dictionary dictionary() {
        return words().stream()
            .reduce(
                Dictionary.empty(),
                Dictionary::define,
                (_, second) -> second
            );
    }

    private Primitives() {
    }

    private static final Word DO_RUNTIME = primitive(
        "(do)", interpreter -> {
            var m = interpreter.machine();
            var index = m.pop();
            var limit = m.pop();
            m.pushReturn(limit);
            m.pushReturn(slot(index, limit));
        }
    );

    private static final Word LOOP_RUNTIME = primitive(
        "(loop)", interpreter -> loopStep(interpreter.machine(), 1)
    );

    private static final Word PLUS_LOOP_RUNTIME = primitive(
        "(+loop)", interpreter -> {
            var m = interpreter.machine();
            loopStep(m, m.pop());
        }
    );

    private static final Word QDO_RUNTIME = primitive(
        "(?do)", interpreter -> {
            var m = interpreter.machine();
            var index = m.pop();
            var limit = m.pop();
            if (limit == index) {
                m.push(0);
            } else {
                m.pushReturn(limit);
                m.pushReturn(slot(index, limit));
                m.push(-1);
            }
        }
    );

    private static final Word UNLOOP_RUNTIME = primitive(
        "(unloop)", interpreter -> {
            var m = interpreter.machine();
            m.popReturn();
            m.popReturn();
        }
    );

    private static List<Word> words() {
        return List.of(
            primitive(
                "DUP", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.peek());
                }
            ),
            primitive("DROP", interpreter -> interpreter.machine().pop()),
            primitive(
                "SWAP", interpreter -> {
                    var m = interpreter.machine();
                    var b = m.pop();
                    var a = m.pop();
                    m.push(b);
                    m.push(a);
                }
            ),
            primitive(
                "OVER", interpreter -> {
                    var m = interpreter.machine();
                    var b = m.pop();
                    var a = m.pop();
                    m.push(a);
                    m.push(b);
                    m.push(a);
                }
            ),
            primitive(
                "ROT", interpreter -> {
                    var m = interpreter.machine();
                    var c = m.pop();
                    var b = m.pop();
                    var a = m.pop();
                    m.push(b);
                    m.push(c);
                    m.push(a);
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
                    var m = interpreter.machine();
                    m.pushReturn(m.pop());
                }
            ),
            primitive(
                "R>", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.popReturn());
                }
            ),
            primitive(
                "R@", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.peekReturn());
                }
            ),
            primitive(
                ".", interpreter -> {
                    var m = interpreter.machine();
                    interpreter.print(formatted(m, m.pop()) + " ");
                }
            ),
            primitive(
                ".R", interpreter -> {
                    var m = interpreter.machine();
                    var width = (int) m.pop();
                    var text = formatted(m, m.pop());
                    interpreter.print(" ".repeat(Math.max(0, width - text.length())) + text);
                }
            ),
            primitive(
                ".S", interpreter -> {
                    var m = interpreter.machine();
                    var stack = m.stack();
                    var text = new StringBuilder("<").append(stack.length).append("> ");
                    for (var value : stack) {
                        text.append(formatted(m, value)).append(' ');
                    }
                    interpreter.print(text.toString());
                }
            ),
            primitive(
                "BASE", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.baseAddress());
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
                        interpreter.append(Word.primitive("(.\")", inner -> inner.print(text)));
                    } else {
                        interpreter.print(text);
                    }
                }
            ),
            immediate(
                "S\"", interpreter -> {
                    var text = interpreter.readUntil('"');
                    var m = interpreter.machine();
                    var address = m.allot(text.length());
                    for (var i = 0; i < text.length(); i++) {
                        m.store(address + i, text.charAt(i));
                    }
                    if (m.compiling()) {
                        interpreter.append(Word.literal(address));
                        interpreter.append(Word.literal(text.length()));
                    } else {
                        m.push(address);
                        m.push(text.length());
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
            immediate("\\", Interpreter::readRestOfLine),
            primitive(":", interpreter -> interpreter.beginDefinition(interpreter.word(":"))),
            immediate(";", Interpreter::endDefinition),
            primitive("IMMEDIATE", Interpreter::makeLatestImmediate),
            immediate("DOES>", interpreter -> interpreter.open().beginTail()),
            primitive(
                "CONSTANT", interpreter -> {
                    var name = interpreter.word("CONSTANT");
                    var value = interpreter.machine().pop();
                    interpreter.define(Word.primitive(name, inner -> inner.machine().push(value)));
                }
            ),
            primitive(
                "VARIABLE", interpreter -> {
                    var name = interpreter.word("VARIABLE");
                    long address = interpreter.machine().allot(1);
                    interpreter.define(Word.primitive(name, inner -> inner.machine().push(address)));
                }
            ),
            primitive(
                "@", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.fetch(m.pop()));
                }
            ),
            primitive(
                "!", interpreter -> {
                    var m = interpreter.machine();
                    var address = m.pop();
                    m.store(address, m.pop());
                }
            ),
            primitive(
                "HERE", interpreter -> {
                    var m = interpreter.machine();
                    m.push(m.here());
                }
            ),
            primitive(
                "ALLOT", interpreter -> {
                    var m = interpreter.machine();
                    m.allot((int) m.pop());
                }
            ),
            unary("CELLS", cells -> cells),
            primitive(
                "+!", interpreter -> {
                    var m = interpreter.machine();
                    var address = m.pop();
                    m.store(address, m.fetch(address) + m.pop());
                }
            ),
            primitive(
                ",", interpreter -> {
                    var m = interpreter.machine();
                    m.store(m.allot(1), m.pop());
                }
            ),
            primitive(
                "CREATE", interpreter -> {
                    var name = interpreter.word("CREATE");
                    long address = interpreter.machine().here();
                    interpreter.define(Word.primitive(name, false, inner -> inner.machine().push(address)));
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
                    var m = interpreter.machine();
                    var ifAt = (int) m.pop();
                    var elseAt = interpreter.open().size();
                    interpreter.append(Word.branch(-1));
                    interpreter.open().resolve(ifAt, interpreter.open().size());
                    m.push(elseAt);
                }
            ),
            immediate(
                "THEN", interpreter -> {
                    var at = (int) interpreter.machine().pop();
                    interpreter.open().resolve(at, interpreter.open().size());
                }
            ),
            immediate(
                "BEGIN", interpreter ->
                    interpreter.machine().push(interpreter.open().size())
            ),
            immediate(
                "UNTIL", interpreter ->
                    interpreter.append(Word.zeroBranch((int) interpreter.machine().pop()))
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
                    var m = interpreter.machine();
                    var whileAt = (int) m.pop();
                    var dest = (int) m.pop();
                    interpreter.append(Word.branch(dest));
                    interpreter.open().resolve(whileAt, interpreter.open().size());
                }
            ),
            immediate(
                "DO", interpreter -> {
                    interpreter.append(DO_RUNTIME);
                    interpreter.open().beginLoop();
                    interpreter.machine().push(interpreter.open().size());
                }
            ),
            immediate(
                "?DO", interpreter -> {
                    interpreter.append(QDO_RUNTIME);
                    var open = interpreter.open();
                    open.beginLoop();
                    var skip = open.size();
                    interpreter.append(Word.zeroBranch(-1));
                    open.addLeave(skip);
                    interpreter.machine().push(open.size());
                }
            ),
            immediate("LOOP", interpreter -> closeLoop(interpreter, LOOP_RUNTIME)),
            immediate("+LOOP", interpreter -> closeLoop(interpreter, PLUS_LOOP_RUNTIME)),
            immediate(
                "LEAVE", interpreter -> {
                    interpreter.append(UNLOOP_RUNTIME);
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
                    var m = interpreter.machine();
                    m.push(index(m.peekReturn(), m.peekReturn(1)));
                }
            ),
            primitive(
                "J", interpreter -> {
                    var m = interpreter.machine();
                    m.push(index(m.peekReturn(2), m.peekReturn(3)));
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
            )
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

    private static void loopStep(Machine m, long increment) {
        var slot = m.popReturn();
        var next = slot + increment;
        var crossed = ((slot ^ next) & (increment ^ next)) < 0;
        if (crossed) {
            m.popReturn();
            m.push(-1);
        } else {
            m.pushReturn(next);
            m.push(0);
        }
    }

    // Loop counters live on the return stack biased: slot = index - limit + MIN_VALUE.
    // The ANS limit-1/limit boundary then sits exactly at MAX_VALUE/MIN_VALUE, so
    // "index crossed the boundary" == "slot + increment overflowed", correct even when
    // the index itself wraps the 64-bit range. I/J reconstruct via index().
    private static long slot(long index, long limit) {
        return index - limit + Long.MIN_VALUE;
    }

    private static long index(long slot, long limit) {
        return slot + limit + Long.MIN_VALUE;
    }

    private static void closeLoop(Interpreter interpreter, Word runtime) {
        var open = interpreter.open();
        var leaves = open.endLoop();
        var dest = (int) interpreter.machine().pop();
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
                var m = interpreter.machine();
                m.push(op.applyAsLong(m.pop()));
            }
        );
    }

    private static Word binary(String name, LongBinaryOperator op) {
        return primitive(
            name, interpreter -> {
                var m = interpreter.machine();
                var b = m.pop();
                var a = m.pop();
                m.push(op.applyAsLong(a, b));
            }
        );
    }

    private static long flag(boolean value) {
        return value ? -1 : 0;
    }

    private static String formatted(Machine m, long value) {
        return Long.toString(value, m.base()).toUpperCase();
    }

    private static String poppedString(Machine m) {
        var length = m.pop();
        var address = m.pop();
        var text = new StringBuilder();
        for (long i = 0; i < length; i++) {
            text.append((char) m.fetch(address + i));
        }
        return text.toString();
    }
}
