package com.github.kjetilv.fjorth;

public interface Fjorth {

    static Fjorth getDefault() {
        return Bootstrap.interpreter(new Machine(), new Stdout());
    }

    Result eval(String line);

    void reset();

    Out out();

    sealed interface Result {

        record OK() implements Result {
        }

        record Failed(String message) implements Result {
        }
    }
}
