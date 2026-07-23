package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PrimesTest extends InterpreterTestCase{

    @Test
    void primes() {
        interpretResource("sieve.fs");
        interpret("main");
        Assertions.assertEquals("1899", output().trim());
    }
}
