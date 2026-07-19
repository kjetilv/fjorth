package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolishTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, new Stdout(output));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    @Test
    void bootstrappedWordsWork() {
        assertArrayEquals(new long[] {1, 2, 1, 2}, stackAfter("1 2 2DUP"));
        machine.reset();
        assertArrayEquals(new long[] {-5, 5}, stackAfter("5 NEGATE -5 ABS"));
        machine.reset();
        assertArrayEquals(new long[] {2, 3}, stackAfter("2 3 MIN 2 3 MAX"));
        machine.reset();
        assertArrayEquals(new long[] {6, 4, -1, 0}, stackAfter("5 1+ 5 1- TRUE FALSE"));
    }

    @Test
    void questionDupDuplicatesOnlyNonZero() {
        assertArrayEquals(new long[] {5, 5, 0}, stackAfter("5 ?DUP 0 ?DUP"));
    }

    @Test
    void spacesEmitsBlanks() {
        interpreter.interpret("3 SPACES");
        assertEquals("   ", output.toString());
    }

    @Test
    void notEqualsAndZeroComparisons() {
        assertArrayEquals(new long[] {-1, 0, -1, -1}, stackAfter("1 2 <> 3 3 <> -1 0< 1 0>"));
    }

    @Test
    void wordsListsDictionaryWithoutDuplicates() {
        interpreter.interpret(": X 1 ; : X 2 ;");
        interpreter.interpret("WORDS");
        var listed = output.toString();
        assertTrue(listed.contains("DUP"));
        assertTrue(listed.contains("2DUP"));
        assertEquals(1, listed.split("\\bX\\b", -1).length - 1);
    }

    @Test
    void seeRendersSimpleDefinitionOnOneLine() {
        interpreter.interpret(": SQUARE DUP * ; SEE SQUARE");
        assertEquals(": SQUARE DUP * ;\n", output.toString());
    }

    @Test
    void seeRendersLiterals() {
        interpreter.interpret(": TEN 10 ; SEE TEN");
        assertEquals(": TEN 10 ;\n", output.toString());
    }

    @Test
    void seeMarksImmediateWords() {
        interpreter.interpret(": M 1 ; IMMEDIATE SEE M");
        assertEquals(": M 1 ; IMMEDIATE\n", output.toString());
    }

    @Test
    void seeRendersBranchesWithIndices() {
        interpreter.interpret(": SIGN 0 < IF -1 ELSE 1 THEN ; SEE SIGN");
        var rendered = output.toString();
        assertTrue(rendered.startsWith(": SIGN\n"));
        assertTrue(rendered.contains("0branch -> "));
        assertTrue(rendered.contains("branch -> "));
        assertTrue(rendered.contains("   0: 0"));
        assertTrue(rendered.endsWith(";\n"));
    }

    @Test
    void seeRendersExit() {
        interpreter.interpret(": F 1 EXIT 2 ; SEE F");
        assertTrue(output.toString().contains("exit"));
    }

    @Test
    void seeOnPrimitive() {
        interpreter.interpret("SEE DUP");
        assertEquals("DUP ( primitive )\n", output.toString());
    }

    @Test
    void seeUnknownWordFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("SEE FROBNICATE"));
    }

    @Test
    void errorsCarryInputPositionContext() {
        var e = assertThrows(
            FjorthException.class,
            () -> interpreter.interpret("1 2 frobnicate")
        );
        assertEquals("frobnicate ?\n1 2 frobnicate\n    ^", e.getMessage());
    }

    @Test
    void errorPositionPointsAtFailingWordNotLineStart() {
        var e = assertThrows(
            FjorthException.class,
            () -> interpreter.interpret("1 0 /")
        );
        assertEquals("division by zero\n1 0 /\n    ^", e.getMessage());
    }
}
