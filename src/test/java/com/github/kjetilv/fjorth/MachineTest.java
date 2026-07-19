package com.github.kjetilv.fjorth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTest {

    @Test
    void pushAndPopAreLifo() {
        Machine machine = new Machine();
        machine.push(1);
        machine.push(2);
        machine.push(3);
        assertEquals(3, machine.pop());
        assertEquals(2, machine.pop());
        assertEquals(1, machine.pop());
        assertEquals(0, machine.depth());
    }

    @Test
    void peekDoesNotConsume() {
        Machine machine = new Machine();
        machine.push(42);
        assertEquals(42, machine.peek());
        assertEquals(1, machine.depth());
        assertEquals(42, machine.pop());
    }

    @Test
    void popOnEmptyStackUnderflows() {
        Machine machine = new Machine();
        ForthException e = assertThrows(ForthException.class, machine::pop);
        assertEquals("stack underflow", e.getMessage());
    }

    @Test
    void peekOnEmptyStackUnderflows() {
        Machine machine = new Machine();
        assertThrows(ForthException.class, machine::peek);
    }

    @Test
    void pushBeyondCapacityOverflows() {
        Machine machine = new Machine(2, 2);
        machine.push(1);
        machine.push(2);
        ForthException e = assertThrows(ForthException.class, () -> machine.push(3));
        assertEquals("stack overflow", e.getMessage());
    }

    @Test
    void returnStackIsIndependentOfDataStack() {
        Machine machine = new Machine();
        machine.push(1);
        machine.pushReturn(2);
        assertEquals(1, machine.depth());
        assertEquals(1, machine.returnDepth());
        assertEquals(2, machine.popReturn());
        assertEquals(1, machine.pop());
    }

    @Test
    void returnStackUnderflows() {
        Machine machine = new Machine();
        ForthException e = assertThrows(ForthException.class, machine::popReturn);
        assertEquals("return stack underflow", e.getMessage());
    }

    @Test
    void returnStackOverflows() {
        Machine machine = new Machine(2, 1);
        machine.pushReturn(1);
        ForthException e = assertThrows(ForthException.class, () -> machine.pushReturn(2));
        assertEquals("return stack overflow", e.getMessage());
    }

    @Test
    void stackReturnsContentsBottomFirst() {
        Machine machine = new Machine();
        machine.push(1);
        machine.push(2);
        machine.push(3);
        assertArrayEquals(new long[] {1, 2, 3}, machine.stack());
    }

    @Test
    void memoryStoreAndFetchRoundTrip() {
        Machine machine = new Machine();
        int address = machine.allot(1);
        machine.store(address, 42);
        assertEquals(42, machine.fetch(address));
    }

    @Test
    void allotAdvancesHere() {
        Machine machine = new Machine();
        int first = machine.allot(2);
        int second = machine.allot(1);
        assertEquals(first + 2, second);
        assertEquals(first + 3, machine.here());
    }

    @Test
    void allotBeyondMemoryFails() {
        Machine machine = new Machine(2, 2, 4);
        machine.allot(3);
        assertThrows(ForthException.class, () -> machine.allot(1));
    }

    @Test
    void baseCellIsReservedAndInitializedToDecimal() {
        Machine machine = new Machine();
        assertEquals(10, machine.base());
        assertEquals(10, machine.fetch(machine.baseAddress()));
        machine.store(machine.baseAddress(), 16);
        assertEquals(16, machine.base());
        machine.store(machine.baseAddress(), 1);
        assertThrows(ForthException.class, machine::base);
    }

    @Test
    void fetchOutOfBoundsFails() {
        Machine machine = new Machine();
        assertThrows(ForthException.class, () -> machine.fetch(-1));
        assertThrows(ForthException.class, () -> machine.store(1_000_000, 1));
    }

    @Test
    void resetClearsStacksAndState() {
        Machine machine = new Machine();
        machine.push(1);
        machine.pushReturn(2);
        machine.compiling(true);
        assertTrue(machine.compiling());
        machine.reset();
        assertEquals(0, machine.depth());
        assertEquals(0, machine.returnDepth());
        assertFalse(machine.compiling());
    }
}
