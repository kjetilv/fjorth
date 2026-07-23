package com.github.kjetilv.fjorth;

import module java.base;

public interface Console {

    default void println(String s) {
        print(s);
        println();
    }

    default void println() {
        print("\n");
        flush();
    }

    default void print(char c) {
        print(Character.toString(c));
    }

    default void flush() {
    }

    default void reset() {
    }

    void print(String string);
}
