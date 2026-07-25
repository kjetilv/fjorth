package com.github.kjetilv.fjorth;

import module java.base;

@SuppressWarnings({"UnusedReturnValue", "unused"})
final class HeapMachine implements MachineApi {

    public static final int CHAR_MASK = 0xFFFF;

    private final long[] dataStack;

    private final long[] returnStack;

    private final long[] memory;

    private final AtomicReference<Interpreter> interpreter = new AtomicReference<>();

    private final int baseAddress;

    private int dataStackTop;

    private int returnStackTop;

    private int here;

    private boolean compiling;

    HeapMachine() {
        this(-1, -1);
    }

    HeapMachine(int dataStackSize, int returnStackSize) {
        this(dataStackSize, returnStackSize, -1);
    }

    HeapMachine(int dataStackSize, int returnStackSize, int memoryCells) {
        this.dataStack = new long[dataStackSize > 0 ? dataStackSize : DEFAULT_STACK_SIZE];
        this.returnStack = new long[returnStackSize > 0 ? returnStackSize : DEFAULT_STACK_SIZE];
        this.memory = new long[memoryCells > 0 ? memoryCells : DEFAULT_MEMORY_CELLS];
        this.baseAddress = allot(1);
        store(baseAddress, 10);
    }

    @Override
    public Interpreter interpreter(Console console, boolean canonical) {
        return canonical
            ? resolveCanonical(console)
            : build(console);
    }

    @Override
    public long peek() {
        return dataStackTop > 0
            ? dataStack[dataStackTop - 1]
            : fail("stack underflow");
    }

    @Override
    public long peekReturn() {
        return returnStackTop > 0
            ? returnStack[returnStackTop - 1]
            : fail("return stack underflow");
    }

    @Override
    public long[] stack() {
        return Arrays.copyOf(dataStack, dataStackTop);
    }

    @Override
    public int baseAddress() {
        return baseAddress;
    }

    @Override
    public int base() {
        var base = fetch(baseAddress);
        return 2 <= base && base <= 36
            ? (int) base
            : fail("invalid BASE: " + base);
    }

    @Override
    public void push(long value) {
        checkOverflow();
        dataStack[dataStackTop++] = value;
    }

    @Override
    public long pop() {
        checkUnderflow();
        return dataStack[--dataStackTop];
    }

    @Override
    public long peek(int offset) {
        return dataStackTop > offset
            ? dataStack[dataStackTop - 1 - offset]
            : fail("stack underflow");
    }

    @Override
    public int depth() {
        return dataStackTop;
    }

    @Override
    public void pushReturn(long value) {
        checkReturnOverflow();
        returnStack[returnStackTop++] = value;
    }

    @Override
    public long popReturn() {
        checkReturnUnderflow();
        return returnStack[--returnStackTop];
    }

    @Override
    public long peekReturn(int offset) {
        return returnStackTop > offset
            ? returnStack[returnStackTop - 1 - offset]
            : fail("return stack underflow");
    }

    @Override
    public int returnDepth() {
        return returnStackTop;
    }

    @Override
    public int allot(int cells) {
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

    @Override
    public int here() {
        return here;
    }

    @Override
    public long fetch(long address) {
        return memory[checkAddress(address)];
    }

    @Override
    public void store(long address, long value) {
        memory[checkAddress(address)] = value;
    }

    @Override
    public void store(long address, long count, long value) {
        if (count > 0) {
            Arrays.fill(
                memory,
                checkAddress(address),
                checkAddress(address + count),
                value
            );
        }
    }

    @Override
    public boolean compiling() {
        return compiling;
    }

    @Override
    public void compiling(boolean compiling) {
        this.compiling = compiling;
    }

    @Override
    public void reset() {
        dataStackTop = 0;
        returnStackTop = 0;
        here = 1;
        compiling = false;
    }

    private Interpreter resolveCanonical(Console console) {
        return interpreter.updateAndGet(existing ->
            existing == null
                ? build(console)
                : existing
        );
    }

    private Interpreter build(Console console) {
        return InterpreterImpl
            .unsealed(this, console == null ? Consoles.stdout() : console)
            .loadLibrary(LIBRARY_RESOURCE)
            .seal();
    }

    private void checkOverflow() {
        if (dataStackTop == dataStack.length) {
            fail("stack overflow");
        }
    }

    private void checkUnderflow() {
        if (dataStackTop == 0) {
            fail("stack underflow");
        }
    }

    private void checkReturnOverflow() {
        if (returnStackTop == returnStack.length) {
            throw new FjorthException("return stack overflow");
        }
    }

    private void checkReturnUnderflow() {
        if (returnStackTop == 0) {
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
