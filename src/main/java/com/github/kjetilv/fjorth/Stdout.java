package com.github.kjetilv.fjorth;

import module java.base;

public final class Stdout implements Out {

    private final PrintWriter writer;

    public Stdout() {
        this((PrintWriter) null);
    }

    public Stdout(StringWriter stringWriter) {
        this(stringWriter == null
            ? null
            : new PrintWriter(stringWriter));
    }

    public Stdout(PrintWriter printWriter) {
        this.writer = printWriter == null
            ? new PrintWriter(System.out)
            : printWriter;
    }

    @Override
    public void println(String s) {
        writer.println(s);
    }

    @Override
    public void print(char c) {
        writer.print(c);
    }

    @Override
    public void print(String string) {
        writer.print(string);
    }

    @Override
    public void flush() {
        writer.flush();
        var failed = writer.checkError();
        if (failed) {
            throw new IllegalStateException("stdout failed");
        }
    }
}
