package com.github.kjetilv.fjorth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Bootstrap {

    private static final String BOOTSTRAP_RESOURCE = "fjorth.fs";

    private Bootstrap() {
    }

    public static Interpreter interpreter(Machine machine, PrintWriter out) {
        Interpreter interpreter = new Interpreter(machine, Primitives.dictionary(), out);
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                    Bootstrap.class.getResourceAsStream(BOOTSTRAP_RESOURCE),
                    "missing resource: " + BOOTSTRAP_RESOURCE
                ),
                StandardCharsets.UTF_8
            ))
        ) {
            reader.lines().forEach(interpreter::interpret);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + BOOTSTRAP_RESOURCE, e);
        }
        return interpreter;
    }
}
