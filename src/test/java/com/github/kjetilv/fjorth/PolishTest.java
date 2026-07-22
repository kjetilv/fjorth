package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolishTest extends InterpreterTestCase {

    @Test
    void bootstrappedWordsWork() {
        stackAfter("1 2 2DUP", 1, 2, 1, 2);
        machine().reset();
        stackAfter("5 NEGATE -5 ABS", -5, 5);
        machine().reset();
        stackAfter("2 3 MIN 2 3 MAX", 2, 3);
        machine().reset();
        stackAfter("5 1+ 5 1- TRUE FALSE", 6, 4, -1, 0);
    }

    @Test
    void questionDupDuplicatesOnlyNonZero() {
        stackAfter("5 ?DUP 0 ?DUP", 5, 5, 0);
    }

    @Test
    void spacesEmitsBlanks() {
        interpret("3 SPACES");
        assertEquals("   ", output());
    }

    @Test
    void notEqualsAndZeroComparisons() {
        stackAfter("1 2 <> 3 3 <> -1 0< 1 0>", -1, 0, -1, -1);
    }

    @Test
    void wordsListsDictionaryWithoutDuplicates() {
        interpret(": X 1 ; : X 2 ;");
        interpret("WORDS");
        var listed = output();
        assertTrue(listed.contains("DUP"));
        assertTrue(listed.contains("2DUP"));
        assertEquals(1, listed.split("\\bX\\b", -1).length - 1);
    }

    @Test
    void seeRendersSimpleDefinitionOnOneLine() {
        interpret(": SQUARE DUP * ; SEE SQUARE");
        assertEquals(": SQUARE DUP * ;\n", output());
    }

    @Test
    void seeRendersLiterals() {
        interpret(": TEN 10 ; SEE TEN");
        assertEquals(": TEN 10 ;\n", output());
    }

    @Test
    void seeMarksImmediateWords() {
        interpret(": M 1 ; IMMEDIATE SEE M");
        assertEquals(": M 1 ; IMMEDIATE\n", output());
    }

    @Test
    void seeRendersBranchesWithIndices() {
        interpret(": SIGN 0 < IF -1 ELSE 1 THEN ; SEE SIGN");
        var rendered = output();
        assertTrue(rendered.startsWith(": SIGN\n"));
        assertTrue(rendered.contains("0branch -> "));
        assertTrue(rendered.contains("branch -> "));
        assertTrue(rendered.contains("   0: 0"));
        assertTrue(rendered.endsWith(";\n"));
    }

    @Test
    void seeRendersExit() {
        interpret(": F 1 EXIT 2 ; SEE F");
        assertTrue(output().contains("exit"));
    }

    @Test
    void seeOnPrimitive() {
        interpret("SEE DUP");
        assertEquals("DUP ( primitive )\n", output());
    }

    @Test
    void seeUnknownWordFails() {
        interpretFailed("SEE FROBNICATE");
    }

    @Test
    void errorsCarryInputPositionContext() {
        var message = interpretFailed("1 2 frobnicate");
        assertEquals("frobnicate ?\n1 2 frobnicate\n    ^", message);
    }

    @Test
    void errorPositionPointsAtFailingWordNotLineStart() {
        var message = interpretFailed("1 0 /");
        assertEquals("division by zero\n1 0 /\n    ^", message);
    }
}
