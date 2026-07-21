package com.github.kjetilv.fjorth;

import module java.base;

final class Bootstrap {

    static Interpreter interpreter(Machine machine, Console console) {
        var interpreter = new Interpreter(machine, Primitives.dictionary(), console);
        try (
            var reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                    Thread.currentThread().getContextClassLoader().getResourceAsStream(BOOTSTRAP_RESOURCE),
                    "missing bootstrap resource: " + BOOTSTRAP_RESOURCE
                ),
                StandardCharsets.UTF_8
            ))
        ) {
            reader.lines().forEach(interpreter::interpretLine);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + BOOTSTRAP_RESOURCE, e);
        }
        return interpreter;
    }

    private Bootstrap() {
    }

    private static final String BOOTSTRAP_RESOURCE = "fjorth.fs";
}
