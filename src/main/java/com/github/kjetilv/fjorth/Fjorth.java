package com.github.kjetilv.fjorth;

public interface Fjorth {

    static Fjorth getDefault() {
        return getDefault((Out) null);
    }

    static Fjorth getDefault(Out std) {
        return Bootstrap.interpreter(
            new Machine(),
            std == null ? Out.std() : std
        );
    }

    Result eval(String line);

    void reset();

    Out out();

    sealed interface Result extends AutoCloseable {

        @Override
        default void close() {
        }

        record OK() implements Result {
        }

        record Failed(String message, Runnable closer) implements Result {

            @Override
            public void close() {
                closer.run();
            }
        }
    }
}
