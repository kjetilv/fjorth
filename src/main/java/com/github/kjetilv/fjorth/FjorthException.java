package com.github.kjetilv.fjorth;

import module java.base;

final class FjorthException extends RuntimeException {

    private final boolean located;

    private final String line;

    private final String lineError;

    FjorthException(String message) {
        this(message, false, null, null);
    }

    private FjorthException(String message, boolean located, String line, String lineError) {
        super(message);
        this.located = located;
        this.line = line;
        this.lineError = lineError;
    }

    FjorthException locate(String line, int position) {
        if (located) {
            return this;
        }
        var locatedException = new FjorthException(
            getMessage(),
            true,
            line,
            " ".repeat(position) + "^"
        );
        locatedException.setStackTrace(this.getStackTrace());
        return locatedException;
    }

    String multiLineMessage() {
        var message = getMessage();
        if (!located) {
            return message;
        }
        return message +
               "\n" + line +
               "\n" + lineError;
    }
}
