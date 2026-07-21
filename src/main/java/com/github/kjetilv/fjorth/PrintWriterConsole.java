package com.github.kjetilv.fjorth;

import module java.base;

final class PrintWriterConsole implements Console {

    private final PrintWriter writer;

    PrintWriterConsole(PrintStream printStream) {
        this(new PrintWriter(printStream));
    }

    PrintWriterConsole(StringWriter stringWriter) {
        this(stringWriter == null
            ? null
            : new PrintWriter(stringWriter));
    }

    PrintWriterConsole(PrintWriter printWriter) {
        this.writer = Objects.requireNonNull(printWriter, "printWriter");
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
