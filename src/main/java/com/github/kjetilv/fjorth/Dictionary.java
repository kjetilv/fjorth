package com.github.kjetilv.fjorth;

import module java.base;

import static com.github.kjetilv.fjorth.Word.*;

final class Dictionary {

    public static Dictionary of(Word word) {
        return new Dictionary(
            null,
            Objects.requireNonNull(word, "word"),
            null
        );
    }

    @SuppressWarnings("SameParameterValue")
    static Dictionary unsealed(Word... words) {
        if (words.length == 0) {
            throw new IllegalStateException("Empty list");
        }
        return new Dictionary(null, null, words);
    }

    private final Dictionary parent;

    private final Map<String, Word> words;

    private final Word word;

    private final String wordLc;

    private Dictionary(Dictionary parent, Word word, Word[] words) {
        var map = words == null
            ? null
            : toMap(words);
        String wordLc = switch (word) {
            case Primitive(var name, var _, var _) -> lc(name);
            case Value(var name, var _) -> lc(name);
            case Colon (var name, var _, var _) -> lc(name);
            case Branch _, Literal _, ZeroBranch _ -> null;
            case null -> null;
        };
        this(parent, map, word, wordLc);
    }

    private Dictionary(
        Dictionary parent,
        Map<String, Word> words,
        Word word,
        String wordLc
    ) {
        this.parent = parent;
        this.words = words;
        this.word = word;
        this.wordLc = wordLc;
    }

    public void insert(Word word) {
        words.put(lc(word.name()), word);
    }

    Dictionary define(Word word) {
        return new Dictionary(this, word, null);
    }

    Dictionary seal() {
        if (words == null) {
            throw new IllegalStateException(this + " cannot be sealed");
        }
        return new Dictionary(
            null,
            Map.copyOf(words),
            null,
            null
        );
    }

    Word latest() {
        return word;
    }

    Stream<String> words() {
        var local = word != null ? Stream.of(word.name())
            : words == null ? Stream.<String>empty()
                : words.values()
                    .stream()
                    .map(Word::name);
        return parent == null
            ? local
            : Stream.concat(local, parent.words());
    }

    Word lookup(String name) {
        return lookupLc(lc(name));
    }

    private Word lookupLc(String nameLc) {
        var word = wordLc != null && wordLc.equals(nameLc) ? this.word
            : words == null ? null
                : words.get(nameLc);
        return word != null ? word
            : parent != null ? parent.lookupLc(nameLc)
                : null;
    }

    private static Map<String, Word> toMap(Word[] words) {
        Map<String, Word> map = LinkedHashMap.newLinkedHashMap(words.length * 2);
        for (Word word : words) {
            map.put(lc(word.name()), word);
        }
        return map;
    }

    private static String lc(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
