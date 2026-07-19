package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryTest {

    private final Machine machine = new Machine();

    private final Interpreter interpreter =
        Bootstrap.interpreter(machine, Out.to(new StringWriter()));

    private long[] stackAfter(String line) {
        interpreter.interpret(line);
        return machine.stack();
    }

    @Test
    void allotAdvancesHere() {
        assertArrayEquals(new long[] {3}, stackAfter("HERE 3 ALLOT HERE SWAP -"));
    }

    @Test
    void cellsIsIdentityInCellAddressedMemory() {
        assertArrayEquals(new long[] {5}, stackAfter("5 CELLS"));
    }

    @Test
    void plusStoreAddsInPlace() {
        assertArrayEquals(new long[] {15}, stackAfter("VARIABLE V 10 V ! 5 V +! V @"));
    }

    @Test
    void createNamesAnAddress() {
        interpreter.interpret("CREATE A 3 CELLS ALLOT");
        interpreter.interpret("11 A ! 22 A 1 + ! 33 A 2 + !");
        assertArrayEquals(new long[] {22, 33, 11}, stackAfter("A 1 + @ A 2 + @ A @"));
    }

    @Test
    void commaCompilesValuesIntoMemory() {
        interpreter.interpret("CREATE NUMS 1 , 2 , 3 ,");
        assertArrayEquals(new long[] {1, 3}, stackAfter("NUMS @ NUMS 2 + @"));
    }

    @Test
    void createdWordsAreDistinct() {
        interpreter.interpret("CREATE A 1 CELLS ALLOT CREATE B 1 CELLS ALLOT");
        assertArrayEquals(new long[] {7, 8}, stackAfter("7 A ! 8 B ! A @ B @"));
    }

    @Test
    void createWorksInsideDefinitions() {
        interpreter.interpret("CREATE TABLE 10 , 20 , 30 ,");
        interpreter.interpret(": NTH TABLE + @ ;");
        assertArrayEquals(new long[] {20}, stackAfter("1 NTH"));
    }

    @Test
    void negativeAllotReclaimsMemory() {
        assertArrayEquals(new long[] {0}, stackAfter("HERE 3 ALLOT -3 ALLOT HERE SWAP -"));
    }

    @Test
    void negativeAllotBelowZeroFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("-10000 ALLOT"));
    }

    @Test
    void allotBeyondMemoryFails() {
        assertThrows(FjorthException.class, () -> interpreter.interpret("100000 ALLOT"));
    }
}
