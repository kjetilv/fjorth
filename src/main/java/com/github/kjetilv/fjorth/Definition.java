package com.github.kjetilv.fjorth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Definition {

    private final String name;

    private final List<Word> body = new ArrayList<>();

    private final Deque<List<Integer>> loops = new ArrayDeque<>();

    private final Word[] self = new Word[1];

    Definition(String name) {
        this.name = name;
    }

    void append(Word word) {
        body.add(word);
    }

    int size() {
        return body.size();
    }

    void resolve(int index, int target) {
        body.set(
            index, switch (body.get(index)) {
                case Word.Branch _ -> Word.branch(target);
                case Word.ZeroBranch _ -> new Word.ZeroBranch(target);
                case Word word -> throw new ForthException("not a branch: " + word.name());
            }
        );
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
        if (body.stream().anyMatch(Definition::unresolved)) {
            throw new ForthException("unresolved branch in " + name);
        }
        Word.Colon colon = Word.colon(name, false, body);
        self[0] = colon;
        return colon;
    }

    private static boolean unresolved(Word word) {
        return switch (word) {
            case Word.Branch(int target) -> target < 0;
            case Word.ZeroBranch(int target) -> target < 0;
            default -> false;
        };
    }
}
