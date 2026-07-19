package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Out.to(output));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    @Test
    void ifElseThen() {
        interpreter.interpret(": SIGN 0 < IF -1 ELSE 1 THEN ;");
        assertArrayEquals(new long[] {-1, 1}, stackAfter("-5 SIGN 5 SIGN"));
    }

    @Test
    void ifWithoutElse() {
        interpreter.interpret(": POSITIVE? DUP 0 < IF DROP 0 THEN ;");
        assertArrayEquals(new long[] {7, 0}, stackAfter("7 POSITIVE? -7 POSITIVE?"));
    }

    @Test
    void nestedIf() {
        interpreter.interpret(": CLASSIFY DUP 0 < IF DROP -1 ELSE 0= IF 0 ELSE 1 THEN THEN ;");
        assertArrayEquals(new long[] {-1, 0, 1}, stackAfter("-9 CLASSIFY 0 CLASSIFY 9 CLASSIFY"));
    }

    @Test
    void beginUntil() {
        interpreter.interpret(": COUNT-UP 0 BEGIN 1 + DUP 5 = UNTIL ;");
        assertArrayEquals(new long[] {5}, stackAfter("COUNT-UP"));
    }

    @Test
    void beginWhileRepeat() {
        interpreter.interpret(": GAUSS 0 SWAP BEGIN DUP 0 > WHILE TUCK + SWAP 1 - REPEAT DROP ;");
        assertArrayEquals(new long[] {15, 5050}, stackAfter("5 GAUSS 100 GAUSS"));
    }

    @Test
    void doLoopSums() {
        interpreter.interpret(": SUM 0 5 0 DO I + LOOP ;");
        assertArrayEquals(new long[] {10}, stackAfter("SUM"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void nestedLoopsWithJ() {
        interpreter.interpret(": TABLE 3 0 DO 2 0 DO J 10 * I + . LOOP LOOP ;");
        interpreter.interpret("TABLE");
        assertEquals("0 1 10 11 20 21 ", output.toString());
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void plusLoopSteps() {
        interpreter.interpret(": EVENS 10 0 DO I 2 +LOOP ;");
        assertArrayEquals(new long[] {0, 2, 4, 6, 8}, stackAfter("EVENS"));
    }

    @Test
    void plusLoopCountsDownIncludingLimit() {
        interpreter.interpret(": COUNTDOWN 0 5 DO I -1 +LOOP ;");
        assertArrayEquals(new long[] {5, 4, 3, 2, 1, 0}, stackAfter("COUNTDOWN"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void plusLoopTerminatesOnBoundaryCrossingRegardlessOfDirection() {
        interpreter.interpret(": OVERSHOOT 5 0 DO I 10 +LOOP ;");
        assertArrayEquals(new long[] {0}, stackAfter("OVERSHOOT"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void questionDoSkipsWhenLimitEqualsIndex() {
        interpreter.interpret(": NONE 0 0 ?DO I LOOP ;");
        assertArrayEquals(new long[] {}, stackAfter("NONE"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void questionDoLoopsWhenLimitsDiffer() {
        interpreter.interpret(": SUM 0 5 0 ?DO I + LOOP ;");
        assertArrayEquals(new long[] {10}, stackAfter("SUM"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void questionDoWithPlusLoopSkips() {
        interpreter.interpret(": NONE 10 10 ?DO I 2 +LOOP ;");
        assertArrayEquals(new long[] {}, stackAfter("NONE"));
    }

    @Test
    void leaveWorksInsideQuestionDo() {
        interpreter.interpret(": FIRST-THREE 10 0 ?DO I I 2 = IF LEAVE THEN LOOP ;");
        assertArrayEquals(new long[] {0, 1, 2}, stackAfter("FIRST-THREE"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void leaveExitsLoopAndCleansUp() {
        interpreter.interpret(": FIRST-FEW 10 0 DO I DUP 3 = IF LEAVE THEN LOOP ;");
        assertArrayEquals(new long[] {0, 1, 2, 3}, stackAfter("FIRST-FEW"));
        assertEquals(0, machine.returnDepth());
    }

    @Test
    void exitReturnsEarly() {
        interpreter.interpret(": F 1 EXIT 2 ;");
        assertArrayEquals(new long[] {1}, stackAfter("F"));
    }

    @Test
    void exitInsideConditional() {
        interpreter.interpret(": DEC DUP 0= IF DROP 99 EXIT THEN 1 - ;");
        assertArrayEquals(new long[] {99, 4}, stackAfter("0 DEC 5 DEC"));
    }

    @Test
    void recursiveFactorial() {
        interpreter.interpret(": FACT DUP 2 < IF DROP 1 ELSE DUP 1 - RECURSE * THEN ;");
        assertArrayEquals(new long[] {1, 120, 2432902008176640000L}, stackAfter("1 FACT 5 FACT 20 FACT"));
    }

    @Test
    void recursiveFibonacci() {
        interpreter.interpret(": FIB DUP 2 < IF EXIT THEN DUP 1 - RECURSE SWAP 2 - RECURSE + ;");
        assertArrayEquals(new long[] {0, 1, 55}, stackAfter("0 FIB 1 FIB 10 FIB"));
    }

    @Test
    void recurseBindsToCurrentDefinitionNotShadow() {
        interpreter.interpret(": F DUP 10 < IF 1 + RECURSE THEN ;");
        interpreter.interpret(": F 0 F ;");
        assertArrayEquals(new long[] {10}, stackAfter("F"));
    }

    @Test
    void loopWithoutDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret(": F LOOP ;"));
        assertTrue(e.getMessage().startsWith("LOOP without DO"));
    }

    @Test
    void leaveOutsideDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret(": F LEAVE ;"));
        assertTrue(e.getMessage().startsWith("LEAVE outside DO"));
    }

    @Test
    void unterminatedDoFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret(": F 5 0 DO I ;"));
        assertTrue(e.getMessage().startsWith("unterminated DO"));
    }

    @Test
    void ifWithoutThenFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret(": F IF 1 ;"));
        assertTrue(e.getMessage().startsWith("unresolved branch"));
    }

    @Test
    void controlFlowOutsideDefinitionFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("1 IF 2 THEN"));
    }
}
