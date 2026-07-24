package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FibTest extends InterpreterTestCase {

    @Test
    void fibonacci() {
        interpretResource("fib.fs");
        interpret("main");
        assertEquals("14930352", output().trim());
    }
}
