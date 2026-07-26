package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class InterpreterTestCase {

    static void reset() {
        console.reset();
        interpreter.reset(baseDictionary);
    }

    static MachineApi machine() {
        return machine;
    }

    static String interpretFailed(String line) {
        var interpret = interpreter.interpret(line);
        assertInstanceOf(
            Interpreter.Result.Failed.class,
            interpret
        );
        return ((Interpreter.Result.Failed) interpret).message();
    }

    static String output() {
        return output.toString();
    }

    static void loadResource(String resource) {
        if (loader.load(resource) instanceof Interpreter.Result.Failed(var message)) {
            throw new IllegalArgumentException("Invalid resource " + resource + ": " + message);
        }
    }

    static void interpret(String line) {
        var interpret = interpreter.interpret(line);
        assertInstanceOf(
            Interpreter.Result.OK.class,
            interpret,
            () -> "Failed to interpret line: \n" + line + "\n " + ((Interpreter.Result.Failed) interpret).message()
        );
    }

    static String outputOf(String line) {
        interpret(line);
        return output.toString();
    }

    static void stackAfter(String line, long... values) {
        interpret(line);
        assertArrayEquals(values, machine.stack());
    }

    static void emptyStackAfter(String line) {
        interpret(line);
        assertArrayEquals(new long[] {}, machine.stack());
    }

    @BeforeEach
    void setup() {
        reset();
    }

    static final MachineApi machine = new HeapMachine();

    private static final StringBuilder output = new StringBuilder();

    static final InterpreterImpl interpreter = (InterpreterImpl)
        machine.interpreter(Consoles.to(output));

    static final Dictionary baseDictionary = interpreter.dictionary();

    static final Loader loader = interpreter.loader();

    static final Console console = Consoles.to(output);
}
