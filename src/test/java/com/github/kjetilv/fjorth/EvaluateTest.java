package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvaluateTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Console.to(output));

    private long[] stackAfter(String line) {
        interpreter.interpretLine(line);
        return machine.stack();
    }

    @Test
    void evaluatesArithmetic() {
        assertArrayEquals(new long[] {3}, stackAfter("S\" 1 2 +\" EVALUATE"));
    }

    @Test
    void outerInputResumesAfterEvaluate() {
        assertArrayEquals(new long[] {1, 2}, stackAfter("S\" 1\" EVALUATE 2"));
    }

    @Test
    void evaluateInsideColonDefinition() {
        interpreter.interpretLine(": RUN S\" 4 5 *\" EVALUATE ;");
        assertArrayEquals(new long[] {20}, stackAfter("RUN"));
    }

    @Test
    void evaluateCanDefineWords() {
        interpreter.interpretLine("S\" : SQ DUP * ;\" EVALUATE");
        assertArrayEquals(new long[] {36}, stackAfter("6 SQ"));
    }

    @Test
    void nestedEvaluate() {
        interpreter.interpretLine(": INNER S\" 2 3 +\" EVALUATE ;");
        interpreter.interpretLine(": OUTER S\" INNER 10 *\" EVALUATE ;");
        assertArrayEquals(new long[] {50}, stackAfter("OUTER"));
    }

    @Test
    void compileStateStartedInsideEvaluatePersists() {
        interpreter.interpretLine("S\" : ANSWER\" EVALUATE");
        assertTrue(machine.compiling());
        interpreter.interpretLine("42 ;");
        assertArrayEquals(new long[] {42}, stackAfter("ANSWER"));
    }

    @Test
    void emptyStringIsANoOp() {
        assertArrayEquals(new long[] {7}, stackAfter("7 S\" \" EVALUATE"));
    }

    @Test
    void errorIsLocatedInTheEvaluatedText() {
        var e = assertThrows(
            FjorthException.class,
            () -> interpreter.interpretLine("S\" 1 frobnicate\" EVALUATE")
        );
        assertEquals("frobnicate ?\n1 frobnicate\n  ^", e.getMessage());
    }
}
