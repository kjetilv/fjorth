package com.github.kjetilv.fjorth;

import java.util.List;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.stream.Collectors;

public final class Primitives {

    public static Dictionary dictionary() {
        return words().stream()
            .reduce(Dictionary.empty(), Dictionary::define, (first, second) -> second);
    }

    private Primitives() {
    }

    private static final Word DO_RUNTIME = primitive(
        "(do)", interpreter -> {
            Machine m = interpreter.machine();
            long index = m.pop();
            long limit = m.pop();
            m.pushReturn(limit);
            m.pushReturn(index);
        }
    );

    private static final Word LOOP_RUNTIME = primitive(
        "(loop)", interpreter -> loopStep(interpreter.machine(), 1)
    );

    private static final Word PLUS_LOOP_RUNTIME = primitive(
        "(+loop)", interpreter -> {
            Machine m = interpreter.machine();
            loopStep(m, m.pop());
        }
    );

    private static final Word QDO_RUNTIME = primitive(
        "(?do)", interpreter -> {
            Machine m = interpreter.machine();
            long index = m.pop();
            long limit = m.pop();
            if (limit == index) {
                m.push(0);
            } else {
                m.pushReturn(limit);
                m.pushReturn(index);
                m.push(-1);
            }
        }
    );

    private static final Word UNLOOP_RUNTIME = primitive(
        "(unloop)", interpreter -> {
            Machine m = interpreter.machine();
            m.popReturn();
            m.popReturn();
        }
    );

    private static List<Word> words() {
        return List.of(
            primitive(
                "DUP", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.peek());
                }
            ),
            primitive("DROP", interpreter -> interpreter.machine().pop()),
            primitive(
                "SWAP", interpreter -> {
                    Machine m = interpreter.machine();
                    long b = m.pop();
                    long a = m.pop();
                    m.push(b);
                    m.push(a);
                }
            ),
            primitive(
                "OVER", interpreter -> {
                    Machine m = interpreter.machine();
                    long b = m.pop();
                    long a = m.pop();
                    m.push(a);
                    m.push(b);
                    m.push(a);
                }
            ),
            primitive(
                "ROT", interpreter -> {
                    Machine m = interpreter.machine();
                    long c = m.pop();
                    long b = m.pop();
                    long a = m.pop();
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
                        throw new ForthException("division by zero");
                    }
                    return a / b;
                }
            ),
            binary(
                "MOD", (a, b) -> {
                    if (b == 0) {
                        throw new ForthException("division by zero");
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
                    Machine m = interpreter.machine();
                    m.pushReturn(m.pop());
                }
            ),
            primitive(
                "R>", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.popReturn());
                }
            ),
            primitive(
                "R@", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.peekReturn());
                }
            ),
            primitive(
                ".", interpreter -> {
                    Machine m = interpreter.machine();
                    interpreter.print(formatted(m, m.pop()) + " ");
                }
            ),
            primitive(
                ".R", interpreter -> {
                    Machine m = interpreter.machine();
                    int width = (int) m.pop();
                    String text = formatted(m, m.pop());
                    interpreter.print(" ".repeat(Math.max(0, width - text.length())) + text);
                }
            ),
            primitive(
                ".S", interpreter -> {
                    Machine m = interpreter.machine();
                    long[] stack = m.stack();
                    StringBuilder text = new StringBuilder("<").append(stack.length).append("> ");
                    for (long value : stack) {
                        text.append(formatted(m, value)).append(' ');
                    }
                    interpreter.print(text.toString());
                }
            ),
            primitive(
                "BASE", interpreter -> {
                    Machine m = interpreter.machine();
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
                    String text = interpreter.readUntil('"');
                    if (interpreter.machine().compiling()) {
                        interpreter.append(Word.primitive("(.\")", inner -> inner.print(text)));
                    } else {
                        interpreter.print(text);
                    }
                }
            ),
            immediate(
                "S\"", interpreter -> {
                    String text = interpreter.readUntil('"');
                    Machine m = interpreter.machine();
                    int address = m.allot(text.length());
                    for (int i = 0; i < text.length(); i++) {
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
                    String name = interpreter.word("CONSTANT");
                    long value = interpreter.machine().pop();
                    interpreter.define(Word.primitive(name, inner -> inner.machine().push(value)));
                }
            ),
            primitive(
                "VARIABLE", interpreter -> {
                    String name = interpreter.word("VARIABLE");
                    long address = interpreter.machine().allot(1);
                    interpreter.define(Word.primitive(name, inner -> inner.machine().push(address)));
                }
            ),
            primitive(
                "@", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.fetch(m.pop()));
                }
            ),
            primitive(
                "!", interpreter -> {
                    Machine m = interpreter.machine();
                    long address = m.pop();
                    m.store(address, m.pop());
                }
            ),
            primitive(
                "HERE", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.here());
                }
            ),
            primitive(
                "ALLOT", interpreter -> {
                    Machine m = interpreter.machine();
                    m.allot((int) m.pop());
                }
            ),
            unary("CELLS", cells -> cells),
            primitive(
                "+!", interpreter -> {
                    Machine m = interpreter.machine();
                    long address = m.pop();
                    m.store(address, m.fetch(address) + m.pop());
                }
            ),
            primitive(
                ",", interpreter -> {
                    Machine m = interpreter.machine();
                    m.store(m.allot(1), m.pop());
                }
            ),
            primitive(
                "CREATE", interpreter -> {
                    String name = interpreter.word("CREATE");
                    long address = interpreter.machine().here();
                    interpreter.define(Word.primitive(name, false, inner -> inner.machine().push(address)));
                }
            ),
            immediate(
                "IF", interpreter -> {
                    int at = interpreter.open().size();
                    interpreter.append(Word.zeroBranch(-1));
                    interpreter.machine().push(at);
                }
            ),
            immediate(
                "ELSE", interpreter -> {
                    Machine m = interpreter.machine();
                    int ifAt = (int) m.pop();
                    int elseAt = interpreter.open().size();
                    interpreter.append(Word.branch(-1));
                    interpreter.open().resolve(ifAt, interpreter.open().size());
                    m.push(elseAt);
                }
            ),
            immediate(
                "THEN", interpreter -> {
                    int at = (int) interpreter.machine().pop();
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
                    int at = interpreter.open().size();
                    interpreter.append(Word.zeroBranch(-1));
                    interpreter.machine().push(at);
                }
            ),
            immediate(
                "REPEAT", interpreter -> {
                    Machine m = interpreter.machine();
                    int whileAt = (int) m.pop();
                    int dest = (int) m.pop();
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
                    Definition open = interpreter.open();
                    open.beginLoop();
                    int skip = open.size();
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
                    int at = interpreter.open().size();
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
                    Machine m = interpreter.machine();
                    m.push(m.peekReturn());
                }
            ),
            primitive(
                "J", interpreter -> {
                    Machine m = interpreter.machine();
                    m.push(m.peekReturn(2));
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
                    String name = interpreter.word("SEE");
                    Word word = interpreter.dictionary().lookup(name)
                        .orElseThrow(() -> new ForthException(name + " ?"));
                    interpreter.print(render(word));
                }
            )
        );
    }

    private static String render(Word word) {
        return switch (word) {
            case Word.Colon(String name, boolean immediate, List<Word> body) -> renderColon(name, immediate, body);
            case Word other -> other.name() + " ( primitive )\n";
        };
    }

    private static String renderColon(String name, boolean immediate, List<Word> body) {
        String suffix = immediate ? " IMMEDIATE\n" : "\n";
        if (body.stream().anyMatch(Primitives::isBranch)) {
            StringBuilder text = new StringBuilder(": ").append(name).append('\n');
            for (int i = 0; i < body.size(); i++) {
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
            case Word.Literal(long value) -> Long.toString(value);
            case Word.Branch(int target) -> target == Integer.MAX_VALUE ? "exit" : "branch -> " + target;
            case Word.ZeroBranch(int target) -> "0branch -> " + target;
            case Word other -> other.name();
        };
    }

    private static void loopStep(Machine m, long increment) {
        long index = m.popReturn();
        long limit = m.peekReturn();
        long next = index + increment;
        boolean crossed = index < limit != next < limit;
        if (crossed) {
            m.popReturn();
            m.push(-1);
        } else {
            m.pushReturn(next);
            m.push(0);
        }
    }

    private static void closeLoop(Interpreter interpreter, Word runtime) {
        Definition open = interpreter.open();
        List<Integer> leaves = open.endLoop();
        int dest = (int) interpreter.machine().pop();
        interpreter.append(runtime);
        interpreter.append(Word.zeroBranch(dest));
        int after = open.size();
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
                Machine m = interpreter.machine();
                m.push(op.applyAsLong(m.pop()));
            }
        );
    }

    private static Word binary(String name, LongBinaryOperator op) {
        return primitive(
            name, interpreter -> {
                Machine m = interpreter.machine();
                long b = m.pop();
                long a = m.pop();
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
        long length = m.pop();
        long address = m.pop();
        StringBuilder text = new StringBuilder();
        for (long i = 0; i < length; i++) {
            text.append((char) m.fetch(address + i));
        }
        return text.toString();
    }
}
