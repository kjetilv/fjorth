package com.github.kjetilv.fjorth;

import module java.base;

public interface Out {

    default void println(String s) {
        print(s);
        print("\n");
    };

    default void print(char c) {
        print(Character.toString(c));
    }

    void print(String string);

    void flush();
}
