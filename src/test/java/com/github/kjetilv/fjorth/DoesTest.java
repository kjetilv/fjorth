package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DoesTest extends InterpreterTestCase {

    @Test
    void constantViaDoes() {
        interpret(": CONST CREATE , DOES> @ ;");
        stackAfter("42 CONST ANSWER ANSWER", 42);
    }

    @Test
    void tailDoesNotRunAtDefineTime() {
        interpret(": CONST CREATE , DOES> @ ;");
        emptyStackAfter("42 CONST ANSWER");
    }

    @Test
    void createdWordsAreIndependent() {
        interpret(": CONST CREATE , DOES> @ ;");
        stackAfter("1 CONST ONE 2 CONST TWO ONE TWO ONE", 1, 2, 1);
    }

    @Test
    void arrayWithIndexingBehavior() {
        interpret(": ARRAY CREATE CELLS ALLOT DOES> + ;");
        interpret("10 ARRAY SCORES");
        stackAfter("99 7 SCORES ! 7 SCORES @", 99);
    }

    @Test
    void tailMayComputeBeyondFetching() {
        interpret(": DOUBLER CREATE , DOES> @ 2 * ;");
        stackAfter("21 DOUBLER FORTY-TWO FORTY-TWO", 42);
    }

    @Test
    void tailMayContainControlFlow() {
        interpret(": MAGNITUDE CREATE , DOES> @ DUP 0 < IF NEGATE THEN ;");
        interpret("-7 MAGNITUDE M1 3 MAGNITUDE M2");
        stackAfter("M1 M2", 7, 3);
    }

    @Test
    void definingWordIsReusableAfterItsChildren() {
        interpret(": CONST CREATE , DOES> @ ;");
        interpret("1 CONST A");
        interpret("A CONST B");
        stackAfter("B", 1);
    }

    @Test
    void doesOutsideDefinitionFails() {
        interpretFailed("DOES>");
    }

    @Test
    void multipleDoesFails() {
        var message = interpretFailed(": BAD CREATE DOES> @ DOES> @ ;");
        assertTrue(message.startsWith("multiple DOES>"));
    }

    @Test
    void unresolvedBranchInTailFails() {
        var message = interpretFailed(": BAD CREATE DOES> IF ;");
        assertTrue(message.startsWith("unresolved branch"));
    }

    @Test
    void seeShowsTheRetrofitWord() {
        interpret(": CONST CREATE , DOES> @ ; SEE CONST");
        assertEquals(": CONST CREATE , (does>) ;\n", output());
    }
}
