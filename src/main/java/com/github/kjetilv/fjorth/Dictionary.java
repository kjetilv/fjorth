package com.github.kjetilv.fjorth;

import module java.base;

record Dictionary(Word word, Dictionary parent) {

    public static Dictionary of(Word word) {
        return EMPTY.define(word);
    }

    public Dictionary define(Word word) {
        if (word == null) {
            throw new IllegalArgumentException("null word");
        }
        return new Dictionary(word, this);
    }

    public Optional<Word> latest() {
        return Optional.ofNullable(word);
    }

    public Stream<Word> words() {
        return Stream.iterate(
                this,
                entry -> entry.word != null,
                entry -> entry.parent
            )
            .map(entry -> entry.word);
    }

    public Optional<Word> lookup(String name) {
        for (var entry = this; entry.word != null; entry = entry.parent) {
            if (entry.word.name().equalsIgnoreCase(name)) {
                return Optional.of(entry.word);
            }
        }
        return Optional.empty();
    }

    static final Dictionary EMPTY = new Dictionary(null, null);
}
