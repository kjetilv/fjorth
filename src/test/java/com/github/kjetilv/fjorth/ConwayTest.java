package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ConwayTest extends InterpreterTestCase {

    @Disabled
    @Test
    void conway() {
        loadResource("conways.fs");
        interpret("clear  0 glider show");
    }
}
