package com.github.kjetilv.fjorth;

public interface MachineApi extends Machine{

    long[] stack();

    int baseAddress();

    int base();

    void push(long value);

    default int ipop() {
        var pop = pop();
        try {
            return Math.toIntExact(pop);
        } catch (Exception e) {
            throw new IllegalStateException("Expected int-sized value on stack: " + pop);
        }
    }

    long pop();

    default long peek() {
        return peek(0);
    }

    long peek(int offset);

    int depth();

    void pushReturn(long value);

    long popReturn();

    default long peekReturn() {
        return peekReturn(0);
    }

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
