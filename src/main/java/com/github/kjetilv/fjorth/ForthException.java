package com.github.kjetilv.fjorth;

public final class ForthException extends RuntimeException {

    private final boolean located;

    public ForthException(String message) {
        this(message, false);
    }

    private ForthException(String message, boolean located) {
        super(message);
        this.located = located;
    }

    ForthException locate(String line, int position) {
        return located
            ? this
            : new ForthException(
                getMessage() + "\n" + line + "\n" + " ".repeat(position) + "^",
                true
            );
    }
}
