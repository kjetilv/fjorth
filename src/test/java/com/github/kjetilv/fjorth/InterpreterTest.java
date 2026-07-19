package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Out.to(output));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    private String outputOf(String line) {
        interpreter.interpret(line);
        return output.toString();
    }

    @Test
    void numbersArePushed() {
        assertArrayEquals(new long[] {1, -2, 300}, stackAfter("1 -2 300"));
    }

    @Test
    void unknownWordFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret("frobnicate"));
        assertTrue(e.getMessage().startsWith("frobnicate ?"));
    }

    @Test
    void arithmetic() {
        assertArrayEquals(new long[] {3}, stackAfter("1 2 +"));
        assertArrayEquals(new long[] {3, 7}, stackAfter("10 3 -"));
        assertArrayEquals(new long[] {3, 7, 42}, stackAfter("6 7 *"));
        assertArrayEquals(new long[] {3, 7, 42, 3, 1}, stackAfter("7 2 / 7 2 MOD"));
    }

    @Test
    void divisionByZeroFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("1 0 /"));
        machine.reset();
        assertThrows(FjorthException.class, () -> interpreter.interpret("1 0 MOD"));
    }

    @Test
    void negateAbsMinMax() {
        assertArrayEquals(new long[] {-5}, stackAfter("5 NEGATE"));
        assertArrayEquals(new long[] {-5, 5}, stackAfter("-5 ABS"));
        assertArrayEquals(new long[] {-5, 5, 2}, stackAfter("2 3 MIN"));
        assertArrayEquals(new long[] {-5, 5, 2, 3}, stackAfter("2 3 MAX"));
    }

    @Test
    void stackWords() {
        assertArrayEquals(new long[] {1, 1}, stackAfter("1 DUP"));
        assertArrayEquals(new long[] {1}, stackAfter("DROP"));
        assertArrayEquals(new long[] {2, 1}, stackAfter("2 SWAP"));
        assertArrayEquals(new long[] {2, 1, 2}, stackAfter("OVER"));
        assertArrayEquals(new long[] {1, 2, 2}, stackAfter("ROT"));
        assertArrayEquals(new long[] {1, 2, 2, 2, 2}, stackAfter("2DUP"));
    }

    @Test
    void nipAndTuck() {
        assertArrayEquals(new long[] {2}, stackAfter("1 2 NIP"));
        assertArrayEquals(new long[] {3, 2, 3}, stackAfter("3 TUCK"));
    }

    @Test
    void comparisonsUseForthTruth() {
        assertArrayEquals(new long[] {-1}, stackAfter("1 2 <"));
        assertArrayEquals(new long[] {-1, 0}, stackAfter("1 2 >"));
        assertArrayEquals(new long[] {-1, 0, -1}, stackAfter("5 5 ="));
        assertArrayEquals(new long[] {-1, 0, -1, -1}, stackAfter("0 0="));
    }

    @Test
    void bitwiseLogic() {
        assertArrayEquals(new long[] {4}, stackAfter("6 12 AND"));
        assertArrayEquals(new long[] {4, 14}, stackAfter("6 12 OR"));
        assertArrayEquals(new long[] {4, 14, 10}, stackAfter("6 12 XOR"));
        assertArrayEquals(new long[] {4, 14, 10, -1}, stackAfter("0 INVERT"));
    }

    @Test
    void returnStackWords() {
        assertArrayEquals(new long[] {2, 1, 1}, stackAfter("1 >R 2 R@ R>"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void dotPrintsTopOfStack() {
        assertEquals("42 ", outputOf("42 ."));
        assertEquals(0, machine.depth());
    }

    @Test
    void dotSPrintsWholeStack() {
        assertEquals("<3> 1 2 3 ", outputOf("1 2 3 .S"));
        assertEquals(3, machine.depth());
    }

    @Test
    void emitAndCr() {
        assertEquals("A\n", outputOf("65 EMIT CR"));
    }

    @Test
    void dotQuotePrintsStringWithoutLeadingSpace() {
        assertEquals("hello, world", outputOf(".\" hello, world\""));
    }

    @Test
    void parenCommentIsIgnored() {
        assertArrayEquals(new long[] {1, 2}, stackAfter("1 ( this is a comment ) 2"));
    }

    @Test
    void backslashCommentSkipsRestOfLine() {
        assertArrayEquals(new long[] {1}, stackAfter("1 \\ 2 3 4"));
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertArrayEquals(new long[] {7, 7}, stackAfter("7 dup"));
    }

    @Test
    void stateSurvivesAcrossLines() {
        interpreter.interpret("1 2");
        interpreter.interpret("+");
        assertArrayEquals(new long[] {3}, machine.stack());
    }
}
