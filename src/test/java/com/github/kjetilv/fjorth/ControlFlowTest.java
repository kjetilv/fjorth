package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowTest extends InterpreterTestCase {

    @Test
    void ifElseThen() {
        interpret(": SIGN 0 < IF -1 ELSE 1 THEN ;");
        stackAfter("-5 SIGN 5 SIGN", -1, 1);
    }

    @Test
    void ifWithoutElse() {
        interpret(": POSITIVE? DUP 0 < IF DROP 0 THEN ;");
        stackAfter("7 POSITIVE? -7 POSITIVE?", 7, 0);
    }

    @Test
    void nestedIf() {
        interpret(": CLASSIFY DUP 0 < IF DROP -1 ELSE 0= IF 0 ELSE 1 THEN THEN ;");
        stackAfter("-9 CLASSIFY 0 CLASSIFY 9 CLASSIFY", -1, 0, 1);
    }

    @Test
    void beginUntil() {
        interpret(": COUNT-UP 0 BEGIN 1 + DUP 5 = UNTIL ;");
        stackAfter("COUNT-UP", 5);
    }

    @Test
    void beginWhileRepeat() {
        interpret(": GAUSS 0 SWAP BEGIN DUP 0 > WHILE TUCK + SWAP 1 - REPEAT DROP ;");
        stackAfter("5 GAUSS 100 GAUSS", 15, 5050);
    }

    @Test
    void doLoopSums() {
        interpret(": SUM 0 5 0 DO I + LOOP ;");
        stackAfter("SUM", 10);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void nestedLoopsWithJ() {
        interpret(": TABLE 3 0 DO 2 0 DO J 10 * I + . LOOP LOOP ;");
        interpret("TABLE");
        assertEquals("0 1 10 11 20 21 ", output());
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void plusLoopSteps() {
        interpret(": EVENS 10 0 DO I 2 +LOOP ;");
        stackAfter("EVENS", 0, 2, 4, 6, 8);
    }

    @Test
    void plusLoopCountsDownIncludingLimit() {
        interpret(": COUNTDOWN 0 5 DO I -1 +LOOP ;");
        stackAfter("COUNTDOWN", 5, 4, 3, 2, 1, 0);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void plusLoopTerminatesOnBoundaryCrossingRegardlessOfDirection() {
        interpret(": OVERSHOOT 5 0 DO I 10 +LOOP ;");
        stackAfter("OVERSHOOT", 0);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void loopIndexMayWrapTheSignedRange() {
        interpret(": WRAP 0 -9223372036854775806 9223372036854775806 DO 1 + LOOP ;");
        stackAfter("WRAP", 4);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void iIsCorrectAcrossTheWrapBoundary() {
        interpret(": EDGES -9223372036854775807 9223372036854775806 DO I LOOP ;");
        stackAfter("EDGES", 9223372036854775806L, 9223372036854775807L, -9223372036854775808L);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void iAndJReconstructIndicesInNestedLoops() {
        interpret(": PAIRS 12 10 DO 2 0 DO J I LOOP LOOP ;");
        stackAfter("PAIRS", 10, 0, 10, 1, 11, 0, 11, 1);
    }

    @Test
    void questionDoSkipsWhenLimitEqualsIndex() {
        interpret(": NONE 0 0 ?DO I LOOP ;");
        emptyStackAfter("NONE");
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void questionDoLoopsWhenLimitsDiffer() {
        interpret(": SUM 0 5 0 ?DO I + LOOP ;");
        stackAfter("SUM", 10);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void questionDoWithPlusLoopSkips() {
        interpret(": NONE 10 10 ?DO I 2 +LOOP ;");
        emptyStackAfter("NONE");
    }

    @Test
    void leaveWorksInsideQuestionDo() {
        interpret(": FIRST-THREE 10 0 ?DO I I 2 = IF LEAVE THEN LOOP ;");
        stackAfter("FIRST-THREE", 0, 1, 2);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void leaveExitsLoopAndCleansUp() {
        interpret(": FIRST-FEW 10 0 DO I DUP 3 = IF LEAVE THEN LOOP ;");
        stackAfter("FIRST-FEW", 0, 1, 2, 3);
        assertEquals(0, machine().returnDepth());
    }

    @Test
    void exitReturnsEarly() {
        interpret(": F 1 EXIT 2 ;");
        stackAfter("F", 1);
    }

    @Test
    void exitInsideConditional() {
        interpret(": DEC DUP 0= IF DROP 99 EXIT THEN 1 - ;");
        stackAfter("0 DEC 5 DEC", 99, 4);
    }

    @Test
    void recursiveFactorial() {
        interpret(": FACT DUP 2 < IF DROP 1 ELSE DUP 1 - RECURSE * THEN ;");
        stackAfter("1 FACT 5 FACT 20 FACT", 1, 120, 2432902008176640000L);
    }

    @Test
    void recursiveFibonacci() {
        interpret(": FIB DUP 2 < IF EXIT THEN DUP 1 - RECURSE SWAP 2 - RECURSE + ;");
        stackAfter("0 FIB 1 FIB 10 FIB", 0, 1, 55);
    }

    @Test
    void recurseBindsToCurrentDefinitionNotShadow() {
        interpret(": F DUP 10 < IF 1 + RECURSE THEN ;");
        interpret(": F 0 F ;");
        stackAfter("F", 10);
    }

    @Test
    void loopWithoutDoFails() {
        var message = interpretFailed(": F LOOP ;");
        assertTrue(message.startsWith("LOOP without DO"));
    }

    @Test
    void leaveOutsideDoFails() {
        var message = interpretFailed(": F LEAVE ;");
        assertTrue(message.startsWith("LEAVE outside DO"));
    }

    @Test
    void unterminatedDoFails() {
        var message = interpretFailed(": F 5 0 DO I ;");
        assertTrue(message.startsWith("unterminated DO"));
    }

    @Test
    void ifWithoutThenFails() {
        var message = interpretFailed(": F IF 1 ;");
        assertTrue(message.startsWith("unresolved branch"));
    }

    @Test
    void controlFlowOutsideDefinitionFails() {
        interpretFailed("1 IF 2 THEN");
    }
}
