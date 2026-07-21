package com.github.kjetilv.fjorth;

import module java.base;

final class Dictionary {

    public static Dictionary of(Word word) {
        return EMPTY.define(word);
    }

    public static Dictionary of(List<Word> words) {
        return new Dictionary(null, null, words);
    }

    private final Dictionary parent;

    private final Map<String, Word> words;

    private final Word word;

    private final String wordLc;

    private Dictionary(
        Dictionary parent,
        Word word,
        List<Word> words
    ) {
        this(
            parent,
            words == null ? null
                : words.stream()
                    .collect(Collectors.toMap(
                        w -> lc(w.name()),
                        Function.identity(),
                        (w1, w2) -> {
                            throw new IllegalStateException(w1 + " / " + w2);
                        },
                        LinkedHashMap::new
                    )),
            word,
            word instanceof Word.Primitive(var name, var _, var _) ? lc(name)
                : word instanceof Word.Colon(var name, var _, var _) ? lc(name)
                    : null
        );
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

    public void defineInPlace(Word word) {
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

    Optional<Word> latest() {
        return Optional.ofNullable(word);
    }

    Stream<Word> words() {
        var local =
            word != null ? Stream.of(word)
                : words != null ? words.values()
                    .stream()
                    : Stream.<Word>empty();
        return parent == null
            ? local
            : Stream.concat(local, parent.words());
    }

    Optional<Word> lookup(String name) {
        return lookupLc(lc(name));
    }

    private Optional<Word> lookupLc(String nameLc) {
        var found = wordLc != null && wordLc.equals(nameLc) ? word
            : words == null ? null
                : words.get(nameLc);
        return found != null ? Optional.of(found)
            : parent != null ? parent.lookupLc(nameLc)
                : Optional.empty();
    }

    static final Dictionary EMPTY = new Dictionary(null, null, null);

    private static String lc(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
