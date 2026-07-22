package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterImplTest extends InterpreterTestCase {

    @Test
    void numbersArePushed() {
        stackAfter("1 -2 300", 1, -2, 300);
    }

    @Test
    void unknownWordFails() {
        var message = interpretFailed("frobnicate");
        assertTrue(message.startsWith("frobnicate ?"));
    }

    @Test
    void arithmetic() {
        stackAfter("1 2 +", 3);
        stackAfter("10 3 -", 3, 7);
        stackAfter("6 7 *", 3, 7, 42);
        stackAfter("7 2 / 7 2 MOD", 3, 7, 42, 3, 1);
    }

    @Test
    void divisionByZeroFails() {
        interpretFailed("1 0 /");
        machine().reset();
        interpretFailed("1 0 MOD");
    }

    @Test
    void negateAbsMinMax() {
        stackAfter("5 NEGATE", -5);
        stackAfter("-5 ABS", -5, 5);
        stackAfter("2 3 MIN", -5, 5, 2);
        stackAfter("2 3 MAX", -5, 5, 2, 3);
    }

    @Test
    void stackWords() {
        stackAfter("1 DUP", 1, 1);
        stackAfter("DROP", 1);
        stackAfter("2 SWAP", 2, 1);
        stackAfter("OVER", 2, 1, 2);
        stackAfter("ROT", 1, 2, 2);
        stackAfter("2DUP", 1, 2, 2, 2, 2);
    }

    @Test
    void nipAndTuck() {
        stackAfter("1 2 NIP", 2);
        stackAfter("3 TUCK", 3, 2, 3);
    }

    @Test
    void comparisonsUseForthTruth() {
        stackAfter("1 2 <", -1);
        stackAfter("1 2 >", -1, 0);
        stackAfter("5 5 =", -1, 0, -1);
        stackAfter("0 0=", -1, 0, -1, -1);
    }

    @Test
    void bitwiseLogic() {
        stackAfter("6 12 AND", 4);
        stackAfter("6 12 OR", 4, 14);
        stackAfter("6 12 XOR", 4, 14, 10);
        stackAfter("0 INVERT", 4, 14, 10, -1);
    }

    @Test
    void returnStackWords() {
        stackAfter("1 >R 2 R@ R>", 2, 1, 1);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void dotPrintsTopOfStack() {
        assertEquals("42 ", outputOf("42 ."));
        assertEquals(0, machine().depth());
    }

    @Test
    void dotSPrintsWholeStack() {
        assertEquals("<3> 1 2 3 ", outputOf("1 2 3 .S"));
        assertEquals(3, machine().depth());
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
        stackAfter("1 ( this is a comment ) 2", 1, 2);
    }

    @Test
    void backslashCommentSkipsRestOfLine() {
        stackAfter("1 \\ 2 3 4", 1);
    }

    @Test
    void lookupIsCaseInsensitive() {
        stackAfter("7 dup", 7, 7);
    }

    @Test
    void stateSurvivesAcrossLines() {
        interpret("1 2");
        interpret("+");
        assertArrayEquals(new long[] {3}, machine().stack());
    }
}
