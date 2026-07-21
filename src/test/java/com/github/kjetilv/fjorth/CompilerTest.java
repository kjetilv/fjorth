package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompilerTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Console.to(output));

    private long[] stackAfter(String line) {
        interpreter.interpretLine(line);
        return machine.stack();
    }

    @Test
    void defineAndCall() {
        assertArrayEquals(new long[] {25}, stackAfter(": SQUARE DUP * ; 5 SQUARE"));
    }

    @Test
    void literalsCompile() {
        assertArrayEquals(new long[] {20}, stackAfter(": TEN 10 ; TEN TEN +"));
    }

    @Test
    void nestedCalls() {
        assertArrayEquals(new long[] {16}, stackAfter(": SQ DUP * ; : QUAD SQ SQ ; 2 QUAD"));
    }

    @Test
    void definitionMaySpanLines() {
        interpreter.interpretLine(": SQUARE");
        interpreter.interpretLine("DUP *");
        interpreter.interpretLine("; 6 SQUARE");
        assertArrayEquals(new long[] {36}, machine.stack());
    }

    @Test
    void redefinitionShadowsButOldCallersKeepOldWord() {
        assertArrayEquals(new long[] {2, 1}, stackAfter(": X 1 ; : Y X ; : X 2 ; X Y"));
    }

    @Test
    void semicolonOutsideDefinitionFails() {
        var e = assertThrows(FjorthException.class, () -> interpreter.interpretLine(";"));
        assertTrue(e.getMessage().startsWith("; outside definition"));
    }

    @Test
    void colonInsideDefinitionFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpretLine(": OUTER : INNER"));
    }

    @Test
    void colonWithoutNameFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpretLine(":"));
    }

    @Test
    void constantPushesItsValue() {
        assertArrayEquals(new long[] {84}, stackAfter("42 CONSTANT ANSWER ANSWER ANSWER +"));
    }

    @Test
    void constantCompilesIntoDefinitions() {
        assertArrayEquals(new long[] {43}, stackAfter("42 CONSTANT ANSWER : NEXT ANSWER 1 + ; NEXT"));
    }

    @Test
    void variableStoresAndFetches() {
        assertArrayEquals(new long[] {7}, stackAfter("VARIABLE V 7 V ! V @"));
    }

    @Test
    void variablesAreDistinct() {
        assertArrayEquals(new long[] {1, 2}, stackAfter("VARIABLE A VARIABLE B 1 A ! 2 B ! A @ B @"));
    }

    @Test
    void immediateWordExecutesDuringCompilation() {
        interpreter.interpretLine(": FIVE 5 ; IMMEDIATE");
        interpreter.interpretLine(": EMPTY FIVE ;");
        assertArrayEquals(new long[] {5}, machine.stack());
        interpreter.interpretLine("EMPTY");
        assertArrayEquals(new long[] {5}, machine.stack());
    }

    @Test
    void dotQuoteCompilesIntoDefinitions() {
        interpreter.interpretLine(": GREET .\" hello\" ; GREET GREET");
        assertEquals("hellohello", output.toString());
    }

    @Test
    void commentsInsideDefinitionsAreSkipped() {
        assertArrayEquals(new long[] {1, 2}, stackAfter(": F 1 ( one ) 2 ; F \\ trailing comment"));
    }

    @Test
    void errorRecoveryDiscardsOpenDefinition() {
        assertThrows(FjorthException.class, () -> interpreter.interpretLine(": BROKEN frobnicate"));
        interpreter.reset();
        assertArrayEquals(new long[] {3}, stackAfter("1 2 +"));
        assertThrows(FjorthException.class, () -> interpreter.interpretLine("BROKEN"));
    }
}
