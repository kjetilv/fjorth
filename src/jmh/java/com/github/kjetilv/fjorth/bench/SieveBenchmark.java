package com.github.kjetilv.fjorth.bench;

import com.github.kjetilv.fjorth.Interpreter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/// Benchmarks `sieve.fs`: `main` runs the Sieve of Eratosthenes 1000 times over
/// an 8190-cell flag array (re-filled per run). Exercises the memory words
/// (`C@`/`C!`/`FILL`), nested `DO`/`+LOOP`, and dictionary lookup under load.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class SieveBenchmark {

    private Interpreter interpreter;

    @Setup(Level.Trial)
    public void setUp() {
        interpreter = Programs.load("sieve.fs");
    }

    @Benchmark
    public Interpreter.Result sieve() {
        return interpreter.interpret("main");
    }
}
