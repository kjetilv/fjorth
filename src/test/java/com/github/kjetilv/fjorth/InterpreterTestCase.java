package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.BeforeEach;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class InterpreterTestCase {

    static void reset() {
        console.reset();
        interpreter.reset(baseDictionary);
    }

    static MachineImpl machine() {
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

    @BeforeEach
    void setup() {
        reset();
    }

    void interpretResource(String resource) {
        Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResourceAsStream(resource))
            .ifPresentOrElse(
                inputStream -> {
                    try (
                        var bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8))
                    ) {
                        bufferedReader.lines()
                            .forEach(this::interpret);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load " + resource, e);
                    }
                },
                () -> {
                    throw new IllegalStateException("No such resource: " + resource);
                }
            );
    }

    void interpret(String line) {
        var interpret = interpreter.interpret(line);
        assertInstanceOf(
            Interpreter.Result.OK.class,
            interpret,
            () -> "Failed to interpret line: \n" + line + "\n " + ((Interpreter.Result.Failed) interpret).message()
        );
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

    private static final StringBuilder output = new StringBuilder();

    static final MachineImpl machine = new MachineImpl();

    static final InterpreterImpl interpreter = (InterpreterImpl)
        machine.interpreter(Consoles.to(output));

    static final Dictionary baseDictionary = interpreter.dictionary();

    static final Console console = Consoles.to(output);
}
