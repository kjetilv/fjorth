package com.github.kjetilv.fjorth;

public interface MachineApi extends Machine{

    char charAt(long address);

    char cpop();

    long[] stack();

    int baseAddress();

    int base();

    void push(long value);

    int ipop();

    long pop();

    long peek();

    long peek(int offset);

    int depth();

    void pushReturn(long value);

    long popReturn();

    long peekReturn();

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
