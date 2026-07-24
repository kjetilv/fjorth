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

/// Benchmarks `fibonacci.fs`: `main` computes fib(34) via naive double recursion
/// (~9.2M recursive calls). A pure-compute workload — exercises `RECURSE`, colon
/// call/return, and arithmetic dispatch with no memory traffic.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class FibonacciBenchmark {

    private Interpreter interpreter;

    @Setup(Level.Trial)
    public void setUp() {
        interpreter = Programs.load("fibonacci.fs");
    }

    @Benchmark
    public Interpreter.Result fibonacci() {
        return interpreter.interpret("main");
    }
}
