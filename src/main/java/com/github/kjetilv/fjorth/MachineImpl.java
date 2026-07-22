package com.github.kjetilv.fjorth;

import module java.base;

@SuppressWarnings("UnusedReturnValue")
public final class MachineImpl implements Machine {

    public static final int CHAR_MASK = 0xFFFF;

    private final long[] data;

    private final long[] returns;

    private final long[] memory;

    private final int baseAddress;

    private int dataTop;

    private int returnsTop;

    private int here;

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
        return InterpreterImpl
            .unsealed(this, console == null ? Console.stdout() : console)
            .loadLibrary(LIBRARY_RESOURCE)
            .seal();
    }

    char charAt(long address) {
        return asChar(memory[checkAddress(address)]);
    }

    char cpop() {
        return asChar(pop());
    }

    long[] stack() {
        return Arrays.copyOf(data, dataTop);
    }

    int baseAddress() {
        return baseAddress;
    }

    int base() {
        var base = fetch(baseAddress);
        return 2 <= base && base <= 36
            ? (int) base
            : fail("invalid BASE: " + base);
    }

    void push(long value) {
        checkOverflow();
        data[dataTop] = value;
        dataTop++;
    }

    int ipop() {
        var pop = pop();
        try {
            return Math.toIntExact(pop);
        } catch (Exception e) {
            throw new IllegalStateException("Expected int-sized value on stack: " + pop);
        }
    }

    long pop() {
        checkUnderflow();
        dataTop--;
        return data[dataTop];
    }

    long peek() {
        return dataTop == 0
            ? fail("stack underflow")
            : data[dataTop - 1];
    }

    long peek(int offset) {
        return dataTop > offset
            ? data[dataTop - 1 - offset]
            : fail("stack underflow");
    }

    int depth() {
        return dataTop;
    }

    void pushReturn(long value) {
        checkReturnOverflow();
        returns[returnsTop] = value;
        returnsTop++;
    }

    long popReturn() {
        checkReturnUnderflow();
        returnsTop--;
        return returns[returnsTop];
    }

    long peekReturn() {
        return returnsTop == 0
            ? fail("return stack underflow")
            : returns[returnsTop - 1];
    }

    long peekReturn(int offset) {
        return returnsTop > offset
            ? returns[returnsTop - 1 - offset]
            : fail("return stack underflow");
    }

    int returnDepth() {
        return returnsTop;
    }

    int allot(int cells) {
        if (here + cells > memory.length) {
            return fail("memory exhausted");
        }
        if (here + cells < 0) {
            return fail("negative ALLOT below memory start");
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

    void store(long address, long count, long value) {
        if (count > 0) {
            var addr = checkAddress(address);
            var toAddr = checkAddress(address + count);
            for (int position = addr; position < toAddr; position++) {
                memory[position] = value;
            }
        }
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

    private void checkOverflow() {
        if (dataTop == data.length) {
            fail("stack overflow");
        }
    }

    private void checkUnderflow() {
        if (dataTop == 0) {
            fail("stack underflow");
        }
    }

    private void checkReturnOverflow() {
        if (returnsTop == returns.length) {
            throw new FjorthException("return stack overflow");
        }
    }

    private void checkReturnUnderflow() {
        if (returnsTop == 0) {
            fail("return stack underflow");
        }
    }

    private int checkAddress(long address) {
        return address >= 0 && address < memory.length
            ? (int) address
            : fail("invalid address: " + address);
    }

    private static final int DEFAULT_STACK_SIZE = 1024;

    private static final int DEFAULT_MEMORY_CELLS = 65536;

    private static final String LIBRARY_RESOURCE = "fjorth.fs";

    private static char asChar(long pop) {
        return (char) (pop & CHAR_MASK);
    }

    private static <T> T fail(String msg) {
        throw new FjorthException(msg);
    }
}
