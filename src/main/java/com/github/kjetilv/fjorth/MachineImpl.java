package com.github.kjetilv.fjorth;

import module java.base;

public final class MachineImpl implements Machine {

    private final long[] data;

    private int dataTop;

    private final long[] returns;

    private int returnsTop;

    private final long[] memory;

    private int here;

    private final int baseAddress;

    private boolean compiling;

    MachineImpl() {
        this(-1, -1);
    }

    MachineImpl(int dataStackSize, int returnStackSize) {
        this(dataStackSize, returnStackSize, -1);
    }

    MachineImpl(int dataStackSize, int returnStackSize, int memoryCells) {
        this.data = new long[dataStackSize > 0 ? dataStackSize : DEFAULT_STACK_SIZE];
        this.returns = new long[returnStackSize > 0 ? returnStackSize : DEFAULT_STACK_SIZE];
        this.memory = new long[memoryCells > 0 ? memoryCells : DEFAULT_MEMORY_CELLS];
        this.baseAddress = allot(1);
        store(baseAddress, 10);
    }

    @Override
    public Interpreter interpreter(Console console) {
        return InterpreterImpl.unsealed(this, console == null ? Console.stdout() : console)
            .loadLibrary(LIBRARY_RESOURCE)
            .seal();
    }

    long[] stack() {
        return Arrays.copyOf(data, dataTop);
    }

    int baseAddress() {
        return baseAddress;
    }

    int base() {
        var base = fetch(baseAddress);
        if (base < 2 || base > 36) {
            throw new FjorthException("invalid BASE: " + base);
        }
        return (int) base;
    }

    void push(long value) {
        if (dataTop == data.length) {
            throw new FjorthException("stack overflow");
        }
        data[dataTop++] = value;
    }

    long pop() {
        if (dataTop == 0) {
            throw new FjorthException("stack underflow");
        }
        return data[--dataTop];
    }

    long peek() {
        if (dataTop == 0) {
            throw new FjorthException("stack underflow");
        }
        return data[dataTop - 1];
    }

    int depth() {
        return dataTop;
    }

    void pushReturn(long value) {
        if (returnsTop == returns.length) {
            throw new FjorthException("return stack overflow");
        }
        returns[returnsTop++] = value;
    }

    long popReturn() {
        if (returnsTop == 0) {
            throw new FjorthException("return stack underflow");
        }
        return returns[--returnsTop];
    }

    long peekReturn() {
        return peekReturn(0);
    }

    long peekReturn(int offset) {
        if (returnsTop <= offset) {
            throw new FjorthException("return stack underflow");
        }
        return returns[returnsTop - 1 - offset];
    }

    int returnDepth() {
        return returnsTop;
    }

    int allot(int cells) {
        if (here + cells > memory.length) {
            throw new FjorthException("memory exhausted");
        }
        if (here + cells < 0) {
            throw new FjorthException("negative ALLOT below memory start");
        }
        var address = here;
        here += cells;
        return address;
    }

    int here() {
        return here;
    }

    long fetch(long address) {
        return memory[checkAddress(address)];
    }

    void store(long address, long value) {
        memory[checkAddress(address)] = value;
    }

    boolean compiling() {
        return compiling;
    }

    void compiling(boolean compiling) {
        this.compiling = compiling;
    }

    void reset() {
        dataTop = 0;
        returnsTop = 0;
        compiling = false;
    }

    private int checkAddress(long address) {
        if (address < 0 || address >= memory.length) {
            throw new FjorthException("invalid address: " + address);
        }
        return (int) address;
    }

    private static final int DEFAULT_STACK_SIZE = 256;

    private static final int DEFAULT_MEMORY_CELLS = 4096;

    private static final String LIBRARY_RESOURCE = "fjorth.fs";
}
