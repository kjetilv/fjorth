package com.github.kjetilv.fjorth;

import module java.base;

final class StringBuilderConsole implements Console {

    private final StringBuilder builder;

    StringBuilderConsole(StringBuilder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    @Override
    public void print(char c) {
        builder.append(c);
    }

    @Override
    public void println(String s) {
        builder.append(s).append("\n");
    }

    @Override
    public void println() {
        builder.append("\n");
        flush();
    }

    @Override
    public void print(String string) {
        builder.append(string);
    }

    @Override
    public void reset() {
        builder.delete(0, builder.length());
    }
}
