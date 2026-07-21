package com.github.kjetilv.fjorth;

public interface Fjorth {

    static Fjorth getDefault() {
        return getDefault((Console) null);
    }

    static Fjorth getDefault(Console std) {
        return Bootstrap.interpreter(
            new Machine(),
            std == null ? Console.stdout() : std
        );
    }

    Result interpret(String line);

    void reset();

    Console console();

    sealed interface Result {

        record OK() implements Result {
        }

        record Failed(String message) implements Result {
        }
    }
}
