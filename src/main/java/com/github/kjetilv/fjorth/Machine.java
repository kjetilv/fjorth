package com.github.kjetilv.fjorth;

public interface Machine {

    static Machine create() {
        return new HeapMachine();
    }

    default Interpreter interpreter() {
        return interpreter(null, true);
    }

    default Interpreter interpreter(Console console) {
        return interpreter(console, false);
    }

    Interpreter interpreter(Console console, boolean canonical);
}
