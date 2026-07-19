package com.github.kjetilv.fjorth;

import module java.base;

sealed interface Word {

    static Primitive primitive(String name, Effect effect) {
        return primitive(name, false, effect);
    }

    static Primitive primitive(String name, boolean immediate, Effect effect) {
        return new Primitive(name, immediate, effect);
    }

    static Colon colon(String name, boolean immediate, List<Word> body) {
        return new Colon(name, immediate, body);
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

    String name();

    boolean immediate();

    record Primitive(String name, boolean immediate, Effect effect) implements Word {
    }

    record Colon(String name, boolean immediate, List<Word> body) implements Word {

        public Colon {
            body = List.copyOf(body);
        }
    }

    record Literal(long value) implements Word {

        @Override
        public String name() {
            return "(literal)";
        }

        @Override
        public boolean immediate() {
            return false;
        }
    }

    record Branch(int target) implements Word {

        @Override
        public String name() {
            return "(branch)";
        }

        @Override
        public boolean immediate() {
            return false;
        }
    }

    record ZeroBranch(int target) implements Word {

        @Override
        public String name() {
            return "(0branch)";
        }

        @Override
        public boolean immediate() {
            return false;
        }
    }

    @FunctionalInterface
    interface Effect {

        @SuppressWarnings("ClassEscapesDefinedScope")
        void apply(Interpreter interpreter);
    }
}
