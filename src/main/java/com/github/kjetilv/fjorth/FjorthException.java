package com.github.kjetilv.fjorth;

import module java.base;

final class FjorthException extends RuntimeException {

    private final boolean located;

    FjorthException(String message) {
        this(message, false);
    }

    private FjorthException(String message, boolean located) {
        super(message);
        this.located = located;
    }

    FjorthException locate(String line, int position) {
        if (located) {
            return this;
        }
        var locatedException = new FjorthException(
            getMessage() + "\n" + line + "\n" + " ".repeat(position) + "^",
            true
        );
        locatedException.setStackTrace(this.getStackTrace());
        return locatedException;
    }
}
