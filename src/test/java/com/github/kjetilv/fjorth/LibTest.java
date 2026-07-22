package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibTest extends InterpreterTestCase {

    @Test
    void returnStack2By2() {

        interpret("10 20");
        assertEquals(0, machine().returnDepth());
        assertEquals(10, machine().peek(1));
        assertEquals(20, machine().peek(0));

        interpret("2>R");
        assertEquals(2, machine().returnDepth());
        assertEquals(10, machine().peekReturn(1));
        assertEquals(20, machine().peekReturn(0));

        interpret("2R>");
        assertEquals(0, machine().returnDepth());
        assertEquals(10, machine().peek(1));

        interpret("+");
        assertEquals(30, machine().peek());
    }
}
