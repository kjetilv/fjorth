package com.github.kjetilv.fjorth;

import module java.base;

sealed interface Word {

    static Primitive primitive(String name, Runnable runnable) {
        return primitive(name, effect(runnable));
    }

    static Primitive primitive(String name, Effect effect) {
        return primitive(name, false, effect);
    }

    static Primitive primitive(String name, boolean immediate, Effect effect) {
        return new Primitive(name, immediate, effect);
    }

    static Value value(String name, Runnable runnable) {
        return value(name, effect(runnable));
    }

    static Value value(String name, Effect effect) {
        return new Value(name, effect);
    }

    static Colon colon(String name, Word... body) {
        return new Colon(name, false, body);
    }

    static Literal literal(long value) {
        return new Literal(value);
    }

    static Branch branch(int target) {
        return new Branch(target);
    }

    static ZeroBranch zeroBranch(int target) {
        return new ZeroBranch(target);
    }

    default boolean immediate() {
        return false;
    }

    default Word makeImmediate() {
        throw new FjorthException("IMMEDIATE: not a colon definition: " + name());
    }

    String name();

    private static Effect effect(Runnable effect) {
        return new RunEffect(effect);
    }

    record Value(String name, Effect effect) implements Word {
    }

    record Primitive(String name, boolean immediate, Effect effect) implements Word {
    }

    record Colon(String name, boolean immediate, Word... body) implements Word {

        @SuppressWarnings("ClassEscapesDefinedScope")
        @Override
        public Word makeImmediate() {
            return new Colon(name, true, body);
        }
    }

    record Literal(long value) implements Word {

        @Override
        public String name() {
            return "(literal)";
        }
    }

    record Branch(int target) implements Word {

        @Override
        public String name() {
            return "(branch)";
        }
    }

    record ZeroBranch(int target) implements Word {

        @Override
        public String name() {
            return "(0branch)";
        }
    }

    @FunctionalInterface
    interface Effect {

        @SuppressWarnings("ClassEscapesDefinedScope")
        void apply(InterpreterImpl interpreter);
    }
}
