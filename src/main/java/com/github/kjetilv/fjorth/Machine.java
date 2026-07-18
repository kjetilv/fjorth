package com.github.kjetilv.fjorth;

import java.util.Arrays;

public final class Machine {

    private static final int DEFAULT_STACK_SIZE = 256;

    private static final int DEFAULT_MEMORY_CELLS = 4096;

    private final long[] data;

    private int dataTop;

    private final long[] returns;

    private int returnsTop;

    private final long[] memory;

    private int here;

    private boolean compiling;

    public Machine() {
        this(DEFAULT_STACK_SIZE, DEFAULT_STACK_SIZE);
    }

    public Machine(int dataStackSize, int returnStackSize) {
        this(dataStackSize, returnStackSize, DEFAULT_MEMORY_CELLS);
    }

    public Machine(int dataStackSize, int returnStackSize, int memoryCells) {
        this.data = new long[dataStackSize];
        this.returns = new long[returnStackSize];
        this.memory = new long[memoryCells];
    }

    public void push(long value) {
        if (dataTop == data.length) {
            throw new ForthException("stack overflow");
        }
        data[dataTop++] = value;
    }

    public long pop() {
        if (dataTop == 0) {
            throw new ForthException("stack underflow");
        }
        return data[--dataTop];
    }

    public long peek() {
        if (dataTop == 0) {
            throw new ForthException("stack underflow");
        }
        return data[dataTop - 1];
    }

    public int depth() {
        return dataTop;
    }

    public long[] stack() {
        return Arrays.copyOf(data, dataTop);
    }

    public void pushReturn(long value) {
        if (returnsTop == returns.length) {
            throw new ForthException("return stack overflow");
        }
        returns[returnsTop++] = value;
    }

    public long popReturn() {
        if (returnsTop == 0) {
            throw new ForthException("return stack underflow");
        }
        return returns[--returnsTop];
    }

    public long peekReturn() {
        return peekReturn(0);
    }

    public long peekReturn(int offset) {
        if (returnsTop <= offset) {
            throw new ForthException("return stack underflow");
        }
        return returns[returnsTop - 1 - offset];
    }

    public int returnDepth() {
        return returnsTop;
    }

    public int allot(int cells) {
        if (here + cells > memory.length) {
            throw new ForthException("memory exhausted");
        }
        if (here + cells < 0) {
            throw new ForthException("negative ALLOT below memory start");
        }
        int address = here;
        here += cells;
        return address;
    }

    public int here() {
        return here;
    }

    public long fetch(long address) {
        return memory[checkAddress(address)];
    }

    public void store(long address, long value) {
        memory[checkAddress(address)] = value;
    }

    public boolean compiling() {
        return compiling;
    }

    public void compiling(boolean compiling) {
        this.compiling = compiling;
    }

    public void reset() {
        dataTop = 0;
        returnsTop = 0;
        compiling = false;
    }

    private int checkAddress(long address) {
        if (address < 0 || address >= memory.length) {
            throw new ForthException("invalid address: " + address);
        }
        return (int) address;
    }
}
