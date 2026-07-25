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
        try {
            return dataStack[dataStackTop - 1];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("stack underflow");
        }
    }

    @Override
    public long peekReturn() {
        try {
            return returnStack[returnStackTop - 1];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("return stack underflow");
        }
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
        try {
            dataStack[dataStackTop++] = value;
        } catch (ArrayIndexOutOfBoundsException _) {
            fail("stack overflow");
        }
    }

    @Override
    public long pop() {
        try {
            return dataStack[--dataStackTop];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("stack underflow");
        }
    }

    @Override
    public long peek(int offset) {
        try {
            return dataStack[dataStackTop - 1 - offset];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("stack underflow");
        }
    }

    @Override
    public int depth() {
        return dataStackTop;
    }

    @Override
    public void pushReturn(long value) {
        try {
            returnStack[returnStackTop++] = value;
        } catch (ArrayIndexOutOfBoundsException _) {
            fail("return stack overflow");
        }
    }

    @Override
    public long popReturn() {
        try {
            return returnStack[--returnStackTop];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("return stack underflow");
        }
    }

    @Override
    public long peekReturn(int offset) {
        try {
            return returnStack[returnStackTop - 1 - offset];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("return stack underflow");
        }
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
        try {
            return memory[Math.toIntExact(address)];
        } catch (ArrayIndexOutOfBoundsException _) {
            return fail("invalid address: " + address);
        }
    }

    @Override
    public void store(long address, long value) {
        try {
            memory[Math.toIntExact(address)] = value;
        } catch (Exception _) {
            fail("invalid address: " + address);
        }
    }

    @Override
    public void store(long address, long count, long value) {
        if (count > 0) {
            try {
                Arrays.fill(
                    memory,
                    Math.toIntExact(address),
                    Math.toIntExact(address + count),
                    value
                );
            } catch (ArrayIndexOutOfBoundsException _) {
                fail("invalid address range: " + address + "+" + count);
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
