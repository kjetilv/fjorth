package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class BubbleSortTest extends InterpreterTestCase {

    @Disabled
    @Test
    void sort() {
        loadResource("bubble-sort.fs");
        interpret("main");
        Assertions.assertEquals("9227465", output().trim());
    }
}
