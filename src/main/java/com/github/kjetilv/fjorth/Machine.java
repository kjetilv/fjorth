package com.github.kjetilv.fjorth;

public interface Machine {

    static Machine create() {
        return new MachineImpl();
    }

    default Interpreter interpreter() {
        return interpreter(null);
    }

    Interpreter interpreter(Console console);
}
