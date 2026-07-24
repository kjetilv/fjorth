package com.github.kjetilv.fjorth;

import module java.base;

final class Dictionary {

    public static Dictionary of(Word word) {
        return new Dictionary(
            null,
            Objects.requireNonNull(word, "word"),
            null
        );
    }

    @SuppressWarnings("SameParameterValue")
    static Dictionary unsealed(List<Word> words) {
        if (Objects.requireNonNull(words, "words").isEmpty()) {
            throw new IllegalStateException("Empty list");
        }
        return new Dictionary(null, null, words);
    }

    private final Dictionary parent;

    private final Map<String, Word> words;

    private final Word word;

    private final String wordLc;

    private Dictionary(Dictionary parent, Word word, List<Word> words) {
        var map = words == null
            ? null
            : toMap(words);
        var wordLc = word instanceof Word.Primitive(var name, var _, var _) ? lc(name)
            : word instanceof Word.Colon(var name, var _, var _) ? lc(name)
                : null;
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
        if (this.words == null) {
            throw new IllegalArgumentException(this + " cannot define in place: " + word);
        }
        words.put(lc(word.name()), word);
    }

    Dictionary define(Word word) {
        if (word == null) {
            throw new IllegalArgumentException("null word");
        }
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

    Stream<Word> words() {
        var local =
            word != null ? Stream.of(word)
                : words == null ? Stream.<Word>empty()
                    : words.values()
                        .stream();
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

    private static Map<String, Word> toMap(List<Word> words) {
        return words.stream()
            .collect(Collectors.toMap(
                w -> lc(w.name()),
                Function.identity(),
                (w1, w2) -> {
                    throw new IllegalStateException(w1 + " / " + w2);
                },
                LinkedHashMap::new
            ));
    }

    private static String lc(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
