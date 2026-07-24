package com.github.kjetilv.fjorth;

import module java.base;

import static com.github.kjetilv.fjorth.Primitives.*;

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

    default boolean immediate() {
        return false;
    }

    String name();

    record Primitive(String name, boolean immediate, Effect effect) implements Word {
    }

    record Colon(String name, boolean immediate, List<Word> body) implements Word {

        public Colon {
            body = List.copyOf(body);
        }

        Word asImmediate() {
            return immediate ? this : new Colon(name, true, body);
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

    sealed interface Effect permits Definition.PrimitiveDoes,
        Abort,
        AbortQuote,
        AddStore,
        Allot,
        Base,
        Begin,
        BeginDefinition,
        BinaryOp,
        Comma,
        Constant,
        Create,
        Definition.PrimitiveDoes.InnerDoes,
        Definition.Recurse,
        Do,
        Dot,
        DotQuote,
        DotR,
        DotS,
        Drop,
        Dup,
        Else,
        Emit,
        EndDefinition,
        Erase,
        Evaluate,
        Exit,
        Fetch,
        FetchChar,
        Fill,
        Here,
        I,
        If,
        ImmediateDoes,
        ImmediateRecurse,
        InnerDo,
        J,
        Leave,
        Leave.PopReturn2,
        Loop,
        Loop.InnerLoop,
        MachinePush,
        Noop,
        Over,
        PeekReturn,
        PlusLoop,
        PlusLoop.InnerPlusLoop,
        PopReturn,
        Primitives.MakeLatestImmediate,
        Print,
        PushReturn,
        QDo,
        QDo.InnerDo,
        ReadRestOfLine,
        ReadToRightPar,
        Repeat,
        Rot,
        SQuote,
        See,
        Store,
        StoreChar,
        Swap,
        Then,
        Type,
        UnaryOp,
        Until,
        Variable,
        While,
        Words
    {

        @SuppressWarnings("ClassEscapesDefinedScope")
        void apply(InterpreterImpl interpreter);
    }
}
