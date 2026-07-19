package com.github.kjetilv.fjorth;

import module java.base;

public interface Out {

    static Out std() {
        return new Stdout();
    }

    static Out to(PrintWriter output) {
        return new Stdout(output);
    }

    static Out to(StringWriter output) {
        return new Stdout(output);
    }

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

    void print(String string);

    void flush();
}
