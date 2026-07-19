package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringNumberTest {

    private final Machine machine = new Machine();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, new Stdout(output));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    @Test
    void baseIsAVariableInitializedToTen() {
        assertArrayEquals(new long[] {10}, stackAfter("BASE @"));
    }

    @Test
    void hexParsing() {
        assertArrayEquals(new long[] {255, 48879, -10}, stackAfter("HEX FF BEEF -A DECIMAL"));
    }

    @Test
    void hexPrinting() {
        interpreter.interpret("255 HEX . DECIMAL");
        assertEquals("FF ", output.toString());
    }

    @Test
    void dotSRespectsBase() {
        interpreter.interpret("10 255 HEX .S DECIMAL");
        assertEquals("<2> A FF ", output.toString());
    }

    @Test
    void compiledLiteralsKeepTheirValueAcrossBaseChanges() {
        interpreter.interpret("HEX : BUMP FF + ; DECIMAL");
        assertArrayEquals(new long[] {256}, stackAfter("1 BUMP"));
    }

    @Test
    void unknownTokenInHexStillFails() {
        interpreter.interpret("HEX");
        assertThrows(FjorthException.class, () -> interpreter.interpret("XYZ"));
        interpreter.interpret("DECIMAL");
    }

    @Test
    void invalidBaseFailsOnNextNumber() {
        interpreter.interpret("1 BASE !");
        var e = assertThrows(FjorthException.class, () -> interpreter.interpret("5"));
        assertTrue(e.getMessage().startsWith("invalid BASE"));
        machine.store(machine.baseAddress(), 10);
    }

    @Test
    void dotRRightAligns() {
        interpreter.interpret("42 5 .R");
        assertEquals("   42", output.toString());
    }

    @Test
    void dotROverflowsFieldWithoutTruncating() {
        interpreter.interpret("12345 3 .R");
        assertEquals("12345", output.toString());
    }

    @Test
    void dotRRespectsBase() {
        interpreter.interpret("255 HEX 4 .R DECIMAL");
        assertEquals("  FF", output.toString());
    }

    @Test
    void sQuotePushesAddressAndLength() {
        assertArrayEquals(new long[] {2}, stackAfter("S\" hi\" NIP"));
    }

    @Test
    void sQuoteStoresCharactersInCells() {
        assertArrayEquals(new long[] {65, 66}, stackAfter("S\" AB\" DROP DUP @ SWAP 1 + @"));
    }

    @Test
    void typePrintsInterpretedString() {
        interpreter.interpret("S\" hello, world\" TYPE");
        assertEquals("hello, world", output.toString());
    }

    @Test
    void sQuoteCompilesIntoDefinitions() {
        interpreter.interpret(": GREET S\" hi\" TYPE ; GREET GREET");
        assertEquals("hihi", output.toString());
    }

    @Test
    void emptyStringHasZeroLength() {
        assertArrayEquals(new long[] {0}, stackAfter("S\" \" NIP"));
    }
}
