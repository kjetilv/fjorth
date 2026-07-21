package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineTest {

    @Test
    void pushAndPopAreLifo() {
        var machine = new MachineImpl();
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
        var machine = new MachineImpl();
        machine.push(42);
        assertEquals(42, machine.peek());
        assertEquals(1, machine.depth());
        assertEquals(42, machine.pop());
    }

    @Test
    void popOnEmptyStackUnderflows() {
        var machine = new MachineImpl();
        var e = assertThrows(FjorthException.class, machine::pop);
        assertEquals("stack underflow", e.getMessage());
    }

    @Test
    void peekOnEmptyStackUnderflows() {
        var machine = new MachineImpl();
        assertThrows(FjorthException.class, machine::peek);
    }

    @Test
    void pushBeyondCapacityOverflows() {
        var machine = new MachineImpl(2, 2);
        machine.push(1);
        machine.push(2);
        var e = assertThrows(FjorthException.class, () -> machine.push(3));
        assertEquals("stack overflow", e.getMessage());
    }

    @Test
    void returnStackIsIndependentOfDataStack() {
        var machine = new MachineImpl();
        machine.push(1);
        machine.pushReturn(2);
        assertEquals(1, machine.depth());
        assertEquals(1, machine.returnDepth());
        assertEquals(2, machine.popReturn());
        assertEquals(1, machine.pop());
    }

    @Test
    void returnStackUnderflows() {
        var machine = new MachineImpl();
        var e = assertThrows(FjorthException.class, machine::popReturn);
        assertEquals("return stack underflow", e.getMessage());
    }

    @Test
    void returnStackOverflows() {
        var machine = new MachineImpl(2, 1);
        machine.pushReturn(1);
        var e = assertThrows(FjorthException.class, () -> machine.pushReturn(2));
        assertEquals("return stack overflow", e.getMessage());
    }

    @Test
    void stackReturnsContentsBottomFirst() {
        var machine = new MachineImpl();
        machine.push(1);
        machine.push(2);
        machine.push(3);
        assertArrayEquals(new long[] {1, 2, 3}, machine.stack());
    }

    @Test
    void memoryStoreAndFetchRoundTrip() {
        var machine = new MachineImpl();
        var address = machine.allot(1);
        machine.store(address, 42);
        assertEquals(42, machine.fetch(address));
    }

    @Test
    void allotAdvancesHere() {
        var machine = new MachineImpl();
        var first = machine.allot(2);
        var second = machine.allot(1);
        assertEquals(first + 2, second);
        assertEquals(first + 3, machine.here());
    }

    @Test
    void allotBeyondMemoryFails() {
        var machine = new MachineImpl(2, 2, 4);
        machine.allot(3);
        assertThrows(FjorthException.class, () -> machine.allot(1));
    }

    @Test
    void baseCellIsReservedAndInitializedToDecimal() {
        var machine = new MachineImpl();
        assertEquals(10, machine.base());
        assertEquals(10, machine.fetch(machine.baseAddress()));
        machine.store(machine.baseAddress(), 16);
        assertEquals(16, machine.base());
        machine.store(machine.baseAddress(), 1);
        assertThrows(FjorthException.class, machine::base);
    }

    @Test
    void fetchOutOfBoundsFails() {
        var machine = new MachineImpl();
        assertThrows(FjorthException.class, () -> machine.fetch(-1));
        assertThrows(FjorthException.class, () -> machine.store(1_000_000, 1));
    }

    @Test
    void resetClearsStacksAndState() {
        var machine = new MachineImpl();
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
