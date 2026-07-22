package com.github.kjetilv.fjorth;

public interface Interpreter {

    Result interpret(String line);

    sealed interface Result {

        record OK() implements Result {
        }

        record Failed(String message) implements Result {
        }
    }
}
