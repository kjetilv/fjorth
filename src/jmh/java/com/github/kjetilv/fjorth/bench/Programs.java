package com.github.kjetilv.fjorth.bench;

import com.github.kjetilv.fjorth.Interpreter;
import com.github.kjetilv.fjorth.Interpreter.Result.Failed;
import com.github.kjetilv.fjorth.Machine;

/// Shared setup for the fjorth JMH benchmarks: build a fresh interpreter with a
/// discarding console and load a `.fs` program (from `src/jmh/resources`) into it.
/// The loaded interpreter can then run its `main` word repeatedly — the two
/// benchmark programs are stack-net-zero and re-initialize their own state per
/// run, so no reset between invocations is needed.
final class Programs {

    static Interpreter load(String resource) {
        Interpreter interpreter = Machine.create().interpreter(DEV_NULL);
        if (interpreter.loader().load(resource) instanceof Failed(var message)) {
            throw new IllegalStateException("failed to read " + resource + ": " + message);
        }
        return interpreter;
    }

    private Programs() {
    }

    private static final com.github.kjetilv.fjorth.Console DEV_NULL = _ -> {
    };
}
