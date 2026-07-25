package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryTest {

    @Test
    void definedWordIsFound() {
        var dup = primitive("DUP");
        var dictionary = Dictionary.of(dup);
        assertSame(dup, dictionary.lookup("DUP"));
    }

    @Test
    void lookupIsCaseInsensitive() {
        var dup = primitive("DUP");
        var dictionary = Dictionary.of(dup);
        assertSame(dup, dictionary.lookup("dup"));
        assertSame(dup, dictionary.lookup("Dup"));
    }

    @Test
    void redefinitionShadowsOlderWord() {
        var first = primitive("X");
        var second = primitive("X");
        var dictionary = Dictionary.of(first).define(second);
        assertSame(second, dictionary.lookup("X"));
    }

    @Test
    void defineDoesNotMutateOriginal() {
        var base = Dictionary.of(primitive("DUP"));
        var extended = base.define(primitive("DUPX"));
        assertNull(base.lookup("DUPX"));
        assertNotNull(extended.lookup("DUPX"));
    }

    @Test
    void compiledReferenceSurvivesRedefinition() {
        var first = primitive("X");
        Word caller = Word.colon("CALLER", first);
        var dictionary = Dictionary.of(first)
            .define(caller)
            .define(primitive("X"));
        var found = dictionary.lookup("CALLER");
        var colon = (Word.Colon) found;
        assertSame(first, colon.body()[0]);
        assertEquals(1, colon.body().length);
    }

    private static Word primitive(String name) {
        return new Word.Primitive(name, false, new Primitives.Noop());
    }
}
