package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FibTest extends InterpreterTestCase{

    @Test
    void fibonacci() {
        interpretResource("fib.fs");
        interpret("main");
        Assertions.assertEquals("9227465", output().trim());
    }
}
