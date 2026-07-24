package com.github.kjetilv.fjorth.bench;

import com.github.kjetilv.fjorth.Console;
import com.github.kjetilv.fjorth.Interpreter;
import com.github.kjetilv.fjorth.Machine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// Shared setup for the fjorth JMH benchmarks: build a fresh interpreter with a
/// discarding console and load a `.fs` program (from `src/jmh/resources`) into it.
/// The loaded interpreter can then run its `main` word repeatedly — the two
/// benchmark programs are stack-net-zero and re-initialize their own state per
/// run, so no reset between invocations is needed.
final class Programs {

    static Interpreter load(String resource) {
        Interpreter interpreter = Machine.create().interpreter(DISCARD);
        try (
            var reader = new BufferedReader(
                new InputStreamReader(stream(resource), StandardCharsets.UTF_8)
            )
        ) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (interpreter.interpret(line) instanceof Interpreter.Result.Failed(var message)) {
                    throw new IllegalStateException(resource + ": " + message + "\n  at: " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + resource, e);
        }
        return interpreter;
    }

    private static InputStream stream(String resource) {
        return Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader().getResourceAsStream(resource),
            () -> "missing benchmark resource: " + resource
        );
    }

    private Programs() {
    }

    private static final Console DISCARD = _ -> {
    };
}
