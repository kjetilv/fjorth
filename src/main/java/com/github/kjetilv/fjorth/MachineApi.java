package com.github.kjetilv.fjorth;

interface MachineApi extends Machine {

    default int ipop() {
        var pop = pop();
        try {
            return Math.toIntExact(pop);
        } catch (Exception e) {
            throw new IllegalStateException("Expected int-sized value on stack: " + pop);
        }
    }

    default long peek() {
        return peek(0);
    }

    default long peekReturn() {
        return peekReturn(0);
    }

    long[] stack();

    int baseAddress();

    int base();

    default void push(boolean value) {
        push(value ? -1 : 0);
    }

    void push(long value);

    long pop();

    long peek(int offset);

    int depth();

    void pushReturn(long value);

    long popReturn();

    long peekReturn(int offset);

    int returnDepth();

    int allot(int cells);

    int here();

    long fetch(long address);

    void store(long address, long value);

    void store(long address, long count, long value);

    boolean compiling();

    void compiling(boolean compiling);

    void reset();
}
