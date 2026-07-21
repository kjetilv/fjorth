package com.github.kjetilv.fjorth;

import module java.base;
import com.github.kjetilv.fjorth.Interpreter.Result.Failed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoesTest {

    private final StringWriter output = new StringWriter();

    private final MachineImpl machine = new MachineImpl();

    private final Interpreter interpreter = machine.interpreter(Console.to(output));

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
        assertInstanceOf(Failed.class, interpreter.interpret("DOES>"));
    }

    @Test
    void multipleDoesFails() {
        var failed = assertInstanceOf(
            Failed.class,
            interpreter.interpret(": BAD CREATE DOES> @ DOES> @ ;")
        );
        assertTrue(failed.message().startsWith("multiple DOES>"));
    }

    @Test
    void unresolvedBranchInTailFails() {
        var failed = assertInstanceOf(
            Failed.class,
            interpreter.interpret(": BAD CREATE DOES> IF ;")
        );
        assertTrue(failed.message().startsWith("unresolved branch"));
    }

    @Test
    void seeShowsTheRetrofitWord() {
        interpreter.interpret(": CONST CREATE , DOES> @ ; SEE CONST");
        assertEquals(": CONST CREATE , (does>) ;\n", output.toString());
    }

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }
}
