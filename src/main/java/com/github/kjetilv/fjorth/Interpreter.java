package com.github.kjetilv.fjorth;

public interface Interpreter {

    Result interpret(String line);

    void interpretInteractively(String line);

    Loader loader();

    sealed interface Result {

        Result OK = new OK();

        record OK() implements Result {
        }

        record Failed(String message) implements Result {
        }
    }
}
