package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoesTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Out.to(output));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    @Test
    void constantViaDoes() {
        interpreter.interpret(": CONST CREATE , DOES> @ ;");
        assertArrayEquals(new long[] {42}, stackAfter("42 CONST ANSWER ANSWER"));
    }

    @Test
    void tailDoesNotRunAtDefineTime() {
        interpreter.interpret(": CONST CREATE , DOES> @ ;");
        assertArrayEquals(new long[] {}, stackAfter("42 CONST ANSWER"));
    }

    @Test
    void createdWordsAreIndependent() {
        interpreter.interpret(": CONST CREATE , DOES> @ ;");
        assertArrayEquals(new long[] {1, 2, 1}, stackAfter("1 CONST ONE 2 CONST TWO ONE TWO ONE"));
    }

    @Test
    void arrayWithIndexingBehavior() {
        interpreter.interpret(": ARRAY CREATE CELLS ALLOT DOES> + ;");
        interpreter.interpret("10 ARRAY SCORES");
        assertArrayEquals(new long[] {99}, stackAfter("99 7 SCORES ! 7 SCORES @"));
    }

    @Test
    void tailMayComputeBeyondFetching() {
        interpreter.interpret(": DOUBLER CREATE , DOES> @ 2 * ;");
        assertArrayEquals(new long[] {42}, stackAfter("21 DOUBLER FORTY-TWO FORTY-TWO"));
    }

    @Test
    void tailMayContainControlFlow() {
        interpreter.interpret(": MAGNITUDE CREATE , DOES> @ DUP 0 < IF NEGATE THEN ;");
        interpreter.interpret("-7 MAGNITUDE M1 3 MAGNITUDE M2");
        assertArrayEquals(new long[] {7, 3}, stackAfter("M1 M2"));
    }

    @Test
    void definingWordIsReusableAfterItsChildren() {
        interpreter.interpret(": CONST CREATE , DOES> @ ;");
        interpreter.interpret("1 CONST A");
        interpreter.interpret("A CONST B");
        assertArrayEquals(new long[] {1}, stackAfter("B"));
    }

    @Test
    void doesOutsideDefinitionFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("DOES>"));
    }

    @Test
    void multipleDoesFails() {
        var e = assertThrows(
            FjorthException.class,
            () -> interpreter.interpret(": BAD CREATE DOES> @ DOES> @ ;")
        );
        assertTrue(e.getMessage().startsWith("multiple DOES>"));
    }

    @Test
    void unresolvedBranchInTailFails() {
        var e = assertThrows(
            FjorthException.class,
            () -> interpreter.interpret(": BAD CREATE DOES> IF ;")
        );
        assertTrue(e.getMessage().startsWith("unresolved branch"));
    }

    @Test
    void seeShowsTheRetrofitWord() {
        interpreter.interpret(": CONST CREATE , DOES> @ ; SEE CONST");
        assertEquals(": CONST CREATE , (does>) ;\n", output.toString());
    }
}
