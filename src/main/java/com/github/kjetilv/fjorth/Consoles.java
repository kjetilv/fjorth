package com.github.kjetilv.fjorth;

import module java.base;

public final class Consoles {

    public static Console stdout() {
        return new PrintWriterConsole(System.out);
    }

    public static Console to(PrintWriter output) {
        return new PrintWriterConsole(output);
    }

    public static Console to(StringWriter output) {
        return new PrintWriterConsole(output);
    }

    public static Console to(StringBuilder builder) {
        return new StringBuilderConsole(builder);
    }

    private Consoles() {
    }
}
