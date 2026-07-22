package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringNumberTest extends InterpreterTestCase {

    @Test
    void baseIsAVariableInitializedToTen() {
        stackAfter("BASE @", 10);
    }

    @Test
    void hexParsing() {
        stackAfter("HEX FF BEEF -A DECIMAL", 255, 48879, -10);
    }

    @Test
    void hexPrinting() {
        interpret("255 HEX . DECIMAL");
        assertEquals("FF ", output());
    }

    @Test
    void dotSRespectsBase() {
        interpret("10 255 HEX .S DECIMAL");
        assertEquals("<2> A FF ", output());
    }

    @Test
    void compiledLiteralsKeepTheirValueAcrossBaseChanges() {
        interpret("HEX : BUMP FF + ; DECIMAL");
        stackAfter("1 BUMP", 256);
    }

    @Test
    void unknownTokenInHexStillFails() {
        interpret("HEX");
        interpretFailed("XYZ");
        interpret("DECIMAL");
    }

    @Test
    void invalidBaseFailsOnNextNumber() {
        interpret("1 BASE !");
        var message = interpretFailed("5");
        assertTrue(message.startsWith("invalid BASE"));
        machine().store(machine().baseAddress(), 10);
    }

    @Test
    void dotRRightAligns() {
        interpret("42 5 .R");
        assertEquals("   42", output());
    }

    @Test
    void dotROverflowsFieldWithoutTruncating() {
        interpret("12345 3 .R");
        assertEquals("12345", output());
    }

    @Test
    void dotRRespectsBase() {
        interpret("255 HEX 4 .R DECIMAL");
        assertEquals("  FF", output());
    }

    @Test
    void sQuotePushesAddressAndLength() {
        stackAfter("S\" hi\" NIP", 2);
    }

    @Test
    void sQuoteStoresCharactersInCells() {
        stackAfter("S\" AB\" DROP DUP @ SWAP 1 + @", 65, 66);
    }

    @Test
    void typePrintsInterpretedString() {
        interpret("S\" hello, world\" TYPE");
        assertEquals("hello, world", output());
    }

    @Test
    void sQuoteCompilesIntoDefinitions() {
        interpret(": GREET S\" hi\" TYPE ; GREET GREET");
        assertEquals("hihi", output());
    }

    @Test
    void emptyStringHasZeroLength() {
        stackAfter("S\" \" NIP", 0);
    }
}
