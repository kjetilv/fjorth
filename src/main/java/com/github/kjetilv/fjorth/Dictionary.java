package com.github.kjetilv.fjorth;

import module java.base;

final class Dictionary {

    public static Dictionary of(Word word) {
        return EMPTY.define(word);
    }

    private final Word word;

    private final Dictionary parent;

    private Dictionary(
        Dictionary parent,
        Word word
    ) {
        this.parent = parent;
        this.word = word;
    }

    Dictionary define(Word word) {
        if (word != null) {
            return new Dictionary(this, word);
        }
        throw new IllegalArgumentException("null word");
    }

    Optional<Word> latest() {
        return Optional.ofNullable(word);
    }

    Stream<Word> words() {
        return Stream.concat(
            Stream.ofNullable(word),
            Stream.ofNullable(parent).flatMap(Dictionary::words)
        );
    }

    Optional<Word> lookup(String name) {
        return found(name).or(() ->
            Optional.ofNullable(parent)
                .flatMap(parent ->
                    parent.lookup(name)));
    }

    private Optional<Word> found(String name) {
        return Optional.ofNullable(word)
            .filter(word ->
                word.name().equalsIgnoreCase(name));
    }

    static final Dictionary EMPTY = new Dictionary(null, null);
}
