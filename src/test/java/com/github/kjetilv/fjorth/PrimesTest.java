package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrimesTest extends InterpreterTestCase {

    @Test
    void primes() {
        loadResource("sieve.fs");
        interpret("main");
        assertEquals("1899", output().trim());
    }
}
