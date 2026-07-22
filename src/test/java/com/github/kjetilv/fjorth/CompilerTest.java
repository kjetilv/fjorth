package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest extends InterpreterTestCase {

    @Test
    void defineAndCall() {
        stackAfter(": SQUARE DUP * ; 5 SQUARE", 25);
    }

    @Test
    void literalsCompile() {
        stackAfter(": TEN 10 ; TEN TEN +", 20);
    }

    @Test
    void nestedCalls() {
        stackAfter(": SQ DUP * ; : QUAD SQ SQ ; 2 QUAD", 16);
    }

    @Test
    void definitionMaySpanLines() {
        interpret(": SQUARE");
        interpret("DUP *");
        stackAfter("; 6 SQUARE", 36);
    }

    @Test
    void redefinitionShadowsButOldCallersKeepOldWord() {
        stackAfter(": X 1 ; : Y X ; : X 2 ; X Y", 2, 1);
    }

    @Test
    void semicolonOutsideDefinitionFails() {
        var message = interpretFailed(";");
        assertTrue(message.startsWith("; outside definition"));
    }

    @Test
    void colonInsideDefinitionFails() {
        interpretFailed(": OUTER : INNER");
    }

    @Test
    void colonWithoutNameFails() {
        interpretFailed(":");
    }

    @Test
    void constantPushesItsValue() {
        stackAfter("42 CONSTANT ANSWER ANSWER ANSWER +", 84);
    }

    @Test
    void constantCompilesIntoDefinitions() {
        stackAfter("42 CONSTANT ANSWER : NEXT ANSWER 1 + ; NEXT", 43);
    }

    @Test
    void variableStoresAndFetches() {
        stackAfter("VARIABLE V 7 V ! V @", 7);
    }

    @Test
    void variablesAreDistinct() {
        stackAfter("VARIABLE A VARIABLE B 1 A ! 2 B ! A @ B @", 1, 2);
    }

    @Test
    void immediateWordExecutesDuringCompilation() {
        interpret(": FIVE 5 ; IMMEDIATE");
        stackAfter(": EMPTY FIVE ;", 5);
        stackAfter("EMPTY", 5);
    }

    @Test
    void dotQuoteCompilesIntoDefinitions() {
        interpret(": GREET .\" hello\" ; GREET GREET");
        assertEquals("hellohello", output());
    }

    @Test
    void commentsInsideDefinitionsAreSkipped() {
        stackAfter(": F 1 ( one ) 2 ; F \\ trailing comment", 1, 2);
    }

    @Test
    void errorRecoveryDiscardsOpenDefinition() {
        interpretFailed(": BROKEN frobnicate");
        stackAfter("1 2 +", 3);
        interpretFailed("BROKEN");
    }
}
