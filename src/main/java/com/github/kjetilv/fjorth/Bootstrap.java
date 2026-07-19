package com.github.kjetilv.fjorth;

import module java.base;

final class Bootstrap {

    static Interpreter interpreter(Machine machine, Out out) {
        var interpreter = new Interpreter(machine, Primitives.dictionary(), out);
        try (
            var reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                    Bootstrap.class.getResourceAsStream(BOOTSTRAP_RESOURCE),
                    "missing resource: " + BOOTSTRAP_RESOURCE
                ),
                StandardCharsets.UTF_8
            ))
        ) {
            reader.lines()
                .forEach(interpreter::interpret);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + BOOTSTRAP_RESOURCE, e);
        }
        return interpreter;
    }

    private Bootstrap() {
    }

    private static final String BOOTSTRAP_RESOURCE = "fjorth.fs";
}
