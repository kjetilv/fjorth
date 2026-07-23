package com.github.kjetilv.fjorth;

import module java.base;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class HeapMachine implements MachineApi {

    public static final int CHAR_MASK = 0xFFFF;

    public static Machine create() {
        return new HeapMachine();
    }

    private final long[] data;

    private final long[] returns;

    private final long[] memory;

    private final AtomicReference<Interpreter> interpreter = new AtomicReference<>();

    private final int baseAddress;

    private int dataTop;

    private int returnsTop;

    private int here;

    private boolean compiling;

    HeapMachine() {
        this(-1, -1);
    }

    HeapMachine(int dataStackSize, int returnStackSize) {
        this(dataStackSize, returnStackSize, -1);
    }

    HeapMachine(int dataStackSize, int returnStackSize, int memoryCells) {
        this.data = new long[dataStackSize > 0 ? dataStackSize : DEFAULT_STACK_SIZE];
        this.returns = new long[returnStackSize > 0 ? returnStackSize : DEFAULT_STACK_SIZE];
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
    public char charAt(long address) {
        return asChar(memory[checkAddress(address)]);
    }

    @Override
    public char cpop() {
        return asChar(pop());
    }

    @Override
    public long[] stack() {
        return Arrays.copyOf(data, dataTop);
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
        data[dataTop] = value;
        dataTop++;
    }

    @Override
    public int ipop() {
        var pop = pop();
        try {
            return Math.toIntExact(pop);
        } catch (Exception e) {
            throw new IllegalStateException("Expected int-sized value on stack: " + pop);
        }
    }

    @Override
    public long pop() {
        checkUnderflow();
        dataTop--;
        return data[dataTop];
    }

    @Override
    public long peek() {
        return dataTop == 0
            ? fail("stack underflow")
            : data[dataTop - 1];
    }

    @Override
    public long peek(int offset) {
        return dataTop > offset
            ? data[dataTop - 1 - offset]
            : fail("stack underflow");
    }

    @Override
    public int depth() {
        return dataTop;
    }

    @Override
    public void pushReturn(long value) {
        checkReturnOverflow();
        returns[returnsTop] = value;
        returnsTop++;
    }

    @Override
    public long popReturn() {
        checkReturnUnderflow();
        returnsTop--;
        return returns[returnsTop];
    }

    @Override
    public long peekReturn() {
        return returnsTop == 0
            ? fail("return stack underflow")
            : returns[returnsTop - 1];
    }

    @Override
    public long peekReturn(int offset) {
        return returnsTop > offset
            ? returns[returnsTop - 1 - offset]
            : fail("return stack underflow");
    }

    @Override
    public int returnDepth() {
        return returnsTop;
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
            var addr = checkAddress(address);
            var toAddr = checkAddress(address + count);
            for (int position = addr; position < toAddr; position++) {
                memory[position] = value;
            }
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
        dataTop = 0;
        returnsTop = 0;
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
