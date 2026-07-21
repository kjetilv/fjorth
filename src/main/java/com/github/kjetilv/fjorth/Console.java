package com.github.kjetilv.fjorth;

import module java.base;

public interface Console {

    static Console stdout() {
        return new PrintWriterConsole(System.out);
    }

    static Console to(PrintWriter output) {
        return new PrintWriterConsole(output);
    }

    static Console to(StringWriter output) {
        return new PrintWriterConsole(output);
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
