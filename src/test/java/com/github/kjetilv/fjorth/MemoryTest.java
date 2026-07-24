package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

class MemoryTest extends InterpreterTestCase {

    @Test
    void allotAdvancesHere() {
        stackAfter("HERE 3 ALLOT HERE SWAP -", 3);
    }

    @Test
    void cellsIsIdentityInCellAddressedMemory() {
        stackAfter("5 CELLS", 5);
    }

    @Test
    void plusStoreAddsInPlace() {
        stackAfter("VARIABLE V 10 V ! 5 V +! V @", 15);
    }

    @Test
    void createNamesAnAddress() {
        interpret("CREATE A 3 CELLS ALLOT");
        interpret("11 A ! 22 A 1 + ! 33 A 2 + !");
        stackAfter("A 1 + @ A 2 + @ A @", 22, 33, 11);
    }

    @Test
    void commaCompilesValuesIntoMemory() {
        interpret("CREATE NUMS 1 , 2 , 3 ,");
        stackAfter("NUMS @ NUMS 2 + @", 1, 3);
    }

    @Test
    void createdWordsAreDistinct() {
        interpret("CREATE A 1 CELLS ALLOT CREATE B 1 CELLS ALLOT");
        stackAfter("7 A ! 8 B ! A @ B @", 7, 8);
    }

    @Test
    void createWorksInsideDefinitions() {
        interpret("CREATE TABLE 10 , 20 , 30 ,");
        interpret(": NTH TABLE + @ ;");
        stackAfter("1 NTH", 20);
    }

    @Test
    void negativeAllotReclaimsMemory() {
        stackAfter("HERE 3 ALLOT -3 ALLOT HERE SWAP -", 0);
    }

    @Test
    void negativeAllotBelowZeroFails() {
        interpretFailed("-10000 ALLOT");
    }

    @Test
    void allotBeyondMemoryFails() {
        interpretFailed("100000 ALLOT");
    }
}
