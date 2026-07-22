package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvaluateTest extends InterpreterTestCase {

    @Test
    void evaluatesArithmetic() {
        stackAfter("S\" 1 2 +\" EVALUATE", 3);
    }

    @Test
    void outerInputResumesAfterEvaluate() {
        stackAfter("S\" 1\" EVALUATE 2", 1, 2);
    }

    @Test
    void evaluateInsideColonDefinition() {
        interpret(": RUN S\" 4 5 *\" EVALUATE ;");
        stackAfter("RUN", 20);
    }

    @Test
    void evaluateCanDefineWords() {
        interpret("S\" : SQ DUP * ;\" EVALUATE");
        stackAfter("6 SQ", 36);
    }

    @Test
    void nestedEvaluate() {
        interpret(": INNER S\" 2 3 +\" EVALUATE ;");
        interpret(": OUTER S\" INNER 10 *\" EVALUATE ;");
        stackAfter("OUTER", 50);
    }

    @Test
    void compileStateStartedInsideEvaluatePersists() {
        interpret("S\" : ANSWER\" EVALUATE");
        assertTrue(machine().compiling());
        interpret("42 ;");
        stackAfter("ANSWER", 42);
    }

    @Test
    void emptyStringIsANoOp() {
        stackAfter("7 S\" \" EVALUATE", 7);
    }

    @Test
    void errorIsLocatedInTheEvaluatedText() {
        var message = interpretFailed("S\" 1 frobnicate\" EVALUATE");
        assertEquals("frobnicate ?\n1 frobnicate\n  ^", message);
    }
}
