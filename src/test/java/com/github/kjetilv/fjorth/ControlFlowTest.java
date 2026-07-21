package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter = Bootstrap.interpreter(machine, Console.to(output));

    @Test
    void ifElseThen() {
        interpreter.interpretLine(": SIGN 0 < IF -1 ELSE 1 THEN ;");
        assertArrayEquals(new long[] {-1, 1}, stackAfter("-5 SIGN 5 SIGN"));
    }

    @Test
    void ifWithoutElse() {
        interpreter.interpretLine(": POSITIVE? DUP 0 < IF DROP 0 THEN ;");
        assertArrayEquals(new long[] {7, 0}, stackAfter("7 POSITIVE? -7 POSITIVE?"));
    }

    @Test
    void nestedIf() {
        interpreter.interpretLine(": CLASSIFY DUP 0 < IF DROP -1 ELSE 0= IF 0 ELSE 1 THEN THEN ;");
        assertArrayEquals(new long[] {-1, 0, 1}, stackAfter("-9 CLASSIFY 0 CLASSIFY 9 CLASSIFY"));
    }

    @Test
    void beginUntil() {
        interpreter.interpretLine(": COUNT-UP 0 BEGIN 1 + DUP 5 = UNTIL ;");
        assertArrayEquals(new long[] {5}, stackAfter("COUNT-UP"));
    }

    @Test
    void beginWhileRepeat() {
        interpreter.interpretLine(": GAUSS 0 SWAP BEGIN DUP 0 > WHILE TUCK + SWAP 1 - REPEAT DROP ;");
        assertArrayEquals(new long[] {15, 5050}, stackAfter("5 GAUSS 100 GAUSS"));
    }

    @Test
    void doLoopSums() {
        interpreter.interpretLine(": SUM 0 5 0 DO I + LOOP ;");
        assertArrayEquals(new long[] {10}, stackAfter("SUM"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void nestedLoopsWithJ() {
        interpreter.interpretLine(": TABLE 3 0 DO 2 0 DO J 10 * I + . LOOP LOOP ;");
        interpreter.interpretLine("TABLE");
        assertEquals("0 1 10 11 20 21 ", output.toString());
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void plusLoopSteps() {
        interpreter.interpretLine(": EVENS 10 0 DO I 2 +LOOP ;");
        assertArrayEquals(new long[] {0, 2, 4, 6, 8}, stackAfter("EVENS"));
    }

    @Test
    void plusLoopCountsDownIncludingLimit() {
        interpreter.interpretLine(": COUNTDOWN 0 5 DO I -1 +LOOP ;");
        assertArrayEquals(new long[] {5, 4, 3, 2, 1, 0}, stackAfter("COUNTDOWN"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void plusLoopTerminatesOnBoundaryCrossingRegardlessOfDirection() {
        interpreter.interpretLine(": OVERSHOOT 5 0 DO I 10 +LOOP ;");
        assertArrayEquals(new long[] {0}, stackAfter("OVERSHOOT"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void loopIndexMayWrapTheSignedRange() {
        interpreter.interpretLine(": WRAP 0 -9223372036854775806 9223372036854775806 DO 1 + LOOP ;");
        assertArrayEquals(new long[] {4}, stackAfter("WRAP"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void iIsCorrectAcrossTheWrapBoundary() {
        interpreter.interpretLine(": EDGES -9223372036854775807 9223372036854775806 DO I LOOP ;");
        assertArrayEquals(
            new long[] {9223372036854775806L, 9223372036854775807L, -9223372036854775808L},
            stackAfter("EDGES")
        );
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void iAndJReconstructIndicesInNestedLoops() {
        interpreter.interpretLine(": PAIRS 12 10 DO 2 0 DO J I LOOP LOOP ;");
        assertArrayEquals(new long[] {10, 0, 10, 1, 11, 0, 11, 1}, stackAfter("PAIRS"));
    }

    @Test
    void questionDoSkipsWhenLimitEqualsIndex() {
        interpreter.interpretLine(": NONE 0 0 ?DO I LOOP ;");
        assertArrayEquals(new long[] {}, stackAfter("NONE"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void questionDoLoopsWhenLimitsDiffer() {
        interpreter.interpretLine(": SUM 0 5 0 ?DO I + LOOP ;");
        assertArrayEquals(new long[] {10}, stackAfter("SUM"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void questionDoWithPlusLoopSkips() {
        interpreter.interpretLine(": NONE 10 10 ?DO I 2 +LOOP ;");
        assertArrayEquals(new long[] {}, stackAfter("NONE"));
    }

    @Test
    void leaveWorksInsideQuestionDo() {
        interpreter.interpretLine(": FIRST-THREE 10 0 ?DO I I 2 = IF LEAVE THEN LOOP ;");
        assertArrayEquals(new long[] {0, 1, 2}, stackAfter("FIRST-THREE"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void leaveExitsLoopAndCleansUp() {
        interpreter.interpretLine(": FIRST-FEW 10 0 DO I DUP 3 = IF LEAVE THEN LOOP ;");
        assertArrayEquals(new long[] {0, 1, 2, 3}, stackAfter("FIRST-FEW"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void exitReturnsEarly() {
        interpreter.interpretLine(": F 1 EXIT 2 ;");
        assertArrayEquals(new long[] {1}, stackAfter("F"));
    }

    @Test
    void exitInsideConditional() {
        interpreter.interpretLine(": DEC DUP 0= IF DROP 99 EXIT THEN 1 - ;");
        assertArrayEquals(new long[] {99, 4}, stackAfter("0 DEC 5 DEC"));
    }

    @Test
    void recursiveFactorial() {
        interpreter.interpretLine(": FACT DUP 2 < IF DROP 1 ELSE DUP 1 - RECURSE * THEN ;");
        assertArrayEquals(new long[] {1, 120, 2432902008176640000L}, stackAfter("1 FACT 5 FACT 20 FACT"));
    }

    @Test
    void recursiveFibonacci() {
        interpreter.interpretLine(": FIB DUP 2 < IF EXIT THEN DUP 1 - RECURSE SWAP 2 - RECURSE + ;");
        assertArrayEquals(new long[] {0, 1, 55}, stackAfter("0 FIB 1 FIB 10 FIB"));
    }

    @Test
    void recurseBindsToCurrentDefinitionNotShadow() {
        interpreter.interpretLine(": F DUP 10 < IF 1 + RECURSE THEN ;");
        interpreter.interpretLine(": F 0 F ;");
        assertArrayEquals(new long[] {10}, stackAfter("F"));
    }

    @Test
    void loopWithoutDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpretLine(": F LOOP ;"));
        assertTrue(e.getMessage().startsWith("LOOP without DO"));
    }

    @Test
    void leaveOutsideDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpretLine(": F LEAVE ;"));
        assertTrue(e.getMessage().startsWith("LEAVE outside DO"));
    }

    @Test
    void unterminatedDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpretLine(": F 5 0 DO I ;"));
        assertTrue(e.getMessage().startsWith("unterminated DO"));
    }

    @Test
    void ifWithoutThenFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpretLine(": F IF 1 ;"));
        assertTrue(e.getMessage().startsWith("unresolved branch"));
    }

    @Test
    void controlFlowOutsideDefinitionFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpretLine("1 IF 2 THEN"));
    }

    private long[] stackAfter(String line) {
        interpreter.interpretLine(line);
        return machine.stack();
    }
}
