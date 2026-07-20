package com.github.kjetilv.fjorth;

import module java.base;

final class Definition {

    private final String name;

    private final List<Word> body = new ArrayList<>();

    private List<Word> tail;

    private final Deque<List<Integer>> loops = new ArrayDeque<>();

    /// Held in an array to allow mutation in lambdas
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

    void resolve(int index, int target) {
        var active = active();
        active.set(
            index, switch (active.get(index)) {
                case Word.Branch _ -> Word.branch(target);
                case Word.ZeroBranch _ -> new Word.ZeroBranch(target);
                case Word word -> throw new FjorthException("not a branch: " + word.name());
            }
        );
    }

    void beginTail() {
        if (tail != null) {
            throw new FjorthException("multiple DOES> in " + name);
        }
        if (inLoop()) {
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

    List<Integer> endLoop() {
        var sites = loops.poll();
        if (sites == null) {
            throw new FjorthException("LOOP without DO");
        }
        return sites;
    }

    Word recurse() {
        return Word.primitive("(recurse)", interpreter -> interpreter.execute(self[0]));
    }

    Word.Colon seal() {
        if (inLoop()) {
            throw new FjorthException("unterminated DO in " + name);
        }
        if (unresolvedBody() || unresolvedTail()) {
            throw new FjorthException("unresolved branch in " + name);
        }
        if (tail != null) {
            body.add(retrofit(Word.colon("(does> " + name + ")", false, tail)));
        }
        var colon = Word.colon(name, false, body);
        self[0] = colon;
        return colon;
    }

    private boolean inLoop() {
        return !loops.isEmpty();
    }

    private boolean unresolvedBody() {
        return body.stream().anyMatch(Definition::unresolved);
    }

    private boolean unresolvedTail() {
        return tail != null && tail.stream().anyMatch(Definition::unresolved);
    }

    private List<Word> active() {
        return tail != null ? tail : body;
    }

    private static Word retrofit(Word.Colon tailColon) {
        return Word.primitive(
            "(does>)", interpreter -> {
                var latest = interpreter.dictionary().latest()
                    .orElseThrow(() -> new FjorthException("DOES>: empty dictionary"));
                interpreter.define(Word.primitive(
                    latest.name(), inner ->
                        inner.execute(latest, tailColon)
                ));
            }
        );
    }

    private static boolean unresolved(Word word) {
        return switch (word) {
            case Word.Branch(var target) -> target < 0;
            case Word.ZeroBranch(var target) -> target < 0;
            default -> false;
        };
    }
}
