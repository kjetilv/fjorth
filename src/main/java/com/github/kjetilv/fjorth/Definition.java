package com.github.kjetilv.fjorth;

import module java.base;

final class Definition {

    private final String name;

    private final List<Word> body = new ArrayList<>(32);

    private List<Word> tail;

    private final Deque<List<Integer>> loops = new ArrayDeque<>();

    private List<Integer> closingLoop;

    /// Works as a _final but mutable cell_ to allow new values set from lambdas
    private final Word[] self = new Word[1];

    Definition(String name) {
        this.name = name;
    }

    void append(Word word) {
        active().add(word);
    }

    int size() {
        return active().size();
    }

    void redirectAt(int index, int target) {
        redirectAt(active(), index, target);
    }

    void beginTail() {
        if (tail != null) {
            throw new FjorthException("multiple DOES> in " + name);
        }
        if (!loops.isEmpty()) {
            throw new FjorthException("unterminated DO before DOES> in " + name);
        }
        tail = new ArrayList<>();
    }

    void beginLoop() {
        loops.push(new ArrayList<>());
    }

    void addLeave(int index) {
        var sites = loops.peek();
        if (sites == null) {
            throw new FjorthException("LEAVE outside DO");
        }
        sites.add(index);
    }

    void endLoop() {
        closingLoop = loops.poll();
        if (closingLoop == null) {
            throw new FjorthException("LOOP without DO");
        }
    }

    void closeLoop() {
        var active = active();
        closingLoop.forEach(site ->
            redirectAt(active, site, active.size()));
    }

    Word recurse() {
        return Word.primitive("(recurse)", new Recurse());
    }

    Word.Colon seal() {
        if (!loops.isEmpty()) {
            throw new FjorthException("unterminated DO in " + name);
        }
        if (unresolved(body) || tail != null && unresolved(tail)) {
            throw new FjorthException("unresolved branch in " + name);
        }
        if (tail != null) {
            var colon = Word.colon("(does> " + name + ")", tail.toArray(Word[]::new));
            body.add(retrofit(colon));
        }
        var colon = Word.colon(name, body.toArray(Word[]::new));
        self[0] = colon;
        return colon;
    }

    private List<Word> active() {
        return tail != null
            ? tail
            : body;
    }

    private static void redirectAt(List<Word> active, int index, int target) {
        var adjusted = adjusted(target, active.get(index));
        active.set(index, adjusted);
    }

    private static Word adjusted(int target, Word word) {
        return switch (word) {
            case Word.Branch(var _) -> Word.branch(target);
            case Word.ZeroBranch(var _) -> new Word.ZeroBranch(target);
            case Word other ->
                throw new FjorthException("primitive, not a branch: " + other.name());
        };
    }

    private static Word retrofit(Word.Colon tailColon) {
        return Word.primitive("(does>)", new Retrofit(tailColon));
    }

    private static boolean unresolved(Iterable<Word> words) {
        for (Word word : words) {
            if (unresolved(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean unresolved(Word word) {
        return switch (word) {
            case Word.Branch(var target) -> target < 0;
            case Word.ZeroBranch(var target) -> target < 0;
            default -> false;
        };
    }

    private final class Recurse implements Word.Effect {

        @Override
        public void apply(InterpreterImpl interpreter) {
            interpreter.execute(self[0]);
        }
    }

    private static final class Retrofit implements Word.Effect {

        private final Word.Colon tailColon;

        private Retrofit(Word.Colon tailColon) {
            this.tailColon = tailColon;
        }

        @Override
        public void apply(InterpreterImpl interpreter) {
            Word latest = interpreter.dictionary().latest();
            String name;
            try {
                name = latest.name();
            } catch (NullPointerException e) {
                throw new FjorthException("DOES>: empty dictionary");
            }
            interpreter.define(Word.primitive(
                name,
                new Does(latest)
            ));
        }

        private final class Does implements Word.Effect {

            private final Word latest;

            private Does(Word latest) {
                this.latest = latest;
            }

            @Override
            public void apply(InterpreterImpl interpreter) {
                interpreter.execute(latest);
                interpreter.execute(tailColon);
            }
        }
    }
}
