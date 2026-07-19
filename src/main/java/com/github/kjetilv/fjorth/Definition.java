package com.github.kjetilv.fjorth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
        List<Word> active = active();
        active.set(
            index, switch (active.get(index)) {
                case Word.Branch _ -> Word.branch(target);
                case Word.ZeroBranch _ -> new Word.ZeroBranch(target);
                case Word word -> throw new ForthException("not a branch: " + word.name());
            }
        );
    }

    void beginTail() {
        if (tail != null) {
            throw new ForthException("multiple DOES> in " + name);
        }
        if (!loops.isEmpty()) {
            throw new ForthException("unterminated DO before DOES> in " + name);
        }
        tail = new ArrayList<>();
    }

    void beginLoop() {
        loops.push(new ArrayList<>());
    }

    void addLeave(int index) {
        List<Integer> sites = loops.peek();
        if (sites == null) {
            throw new ForthException("LEAVE outside DO");
        }
        sites.add(index);
    }

    List<Integer> endLoop() {
        List<Integer> sites = loops.poll();
        if (sites == null) {
            throw new ForthException("LOOP without DO");
        }
        return sites;
    }

    Word recurse() {
        return Word.primitive("(recurse)", interpreter -> interpreter.execute(self[0]));
    }

    Word.Colon seal() {
        if (!loops.isEmpty()) {
            throw new ForthException("unterminated DO in " + name);
        }
        if (unresolvedBody() || unresolvedTail()) {
            throw new ForthException("unresolved branch in " + name);
        }
        if (tail != null) {
            body.add(retrofit(Word.colon("(does> " + name + ")", false, tail)));
        }
        Word.Colon colon = Word.colon(name, false, body);
        self[0] = colon;
        return colon;
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
                Word latest = interpreter.dictionary().latest()
                    .orElseThrow(() -> new ForthException("DOES>: empty dictionary"));
                interpreter.define(Word.primitive(
                    latest.name(), inner -> {
                        inner.execute(latest);
                        inner.execute(tailColon);
                    }
                ));
            }
        );
    }

    private static boolean unresolved(Word word) {
        return switch (word) {
            case Word.Branch(int target) -> target < 0;
            case Word.ZeroBranch(int target) -> target < 0;
            default -> false;
        };
    }
}
