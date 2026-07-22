package com.github.kjetilv.fjorth;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class InterpreterTestCase {

    private final MachineImpl machine = new MachineImpl();

    private final StringWriter output = new StringWriter();

    private final Interpreter interpreter = machine.interpreter(Console.to(output));

    MachineImpl machine() {
        return machine;
    }

    void interpret(String line) {
        assertInstanceOf(
            Interpreter.Result.OK.class,
            interpreter.interpret(line)
        );
    }

    String interpretFailed(String line) {
        var interpret = interpreter.interpret(line);
        assertInstanceOf(
            Interpreter.Result.Failed.class,
            interpret
        );
        return ((Interpreter.Result.Failed) interpret).message();
    }

    String outputOf(String line) {
        interpret(line);
        return output.toString();
    }

    void stackAfter(String line, long... values) {
        interpret(line);
        assertArrayEquals(values, machine.stack());
    }

    void emptyStackAfter(String line) {
        interpret(line);
        assertArrayEquals(new long[] {}, machine.stack());
    }

    String output() {
        return output.toString();
    }
}
