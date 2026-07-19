package com.github.kjetilv.fjorth;

import module java.base;

public final class FjorthException extends RuntimeException {

    private final boolean located;

    FjorthException(String message) {
        this(message, false);
    }

    private FjorthException(String message, boolean located) {
        super(message);
        this.located = located;
    }

    FjorthException locate(String line, int position) {
        return located
            ? this
            : new FjorthException(
                getMessage() + "\n" + line + "\n" + " ".repeat(position) + "^",
                true
            );
    }
}
