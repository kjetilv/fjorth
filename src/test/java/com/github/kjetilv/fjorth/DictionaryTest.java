package com.github.kjetilv.fjorth;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DictionaryTest {

    @Test
    void emptyDictionaryFindsNothing() {
        assertTrue(Dictionary.empty().lookup("DUP").isEmpty());
    }

    @Test
    void definedWordIsFound() {
        var dup = primitive("DUP");
        var dictionary = Dictionary.empty().define(dup);
        assertSame(dup, dictionary.lookup("DUP").orElseThrow());
    }

    @Test
    void lookupIsCaseInsensitive() {
        var dup = primitive("DUP");
        var dictionary = Dictionary.empty().define(dup);
        assertSame(dup, dictionary.lookup("dup").orElseThrow());
        assertSame(dup, dictionary.lookup("Dup").orElseThrow());
    }

    @Test
    void redefinitionShadowsOlderWord() {
        var first = primitive("X");
        var second = primitive("X");
        var dictionary = Dictionary.empty().define(first).define(second);
        assertSame(second, dictionary.lookup("X").orElseThrow());
    }

    @Test
    void defineDoesNotMutateOriginal() {
        var base = Dictionary.empty();
        var extended = base.define(primitive("DUP"));
        assertTrue(base.lookup("DUP").isEmpty());
        assertTrue(extended.lookup("DUP").isPresent());
    }

    @Test
    void compiledReferenceSurvivesRedefinition() {
        var first = primitive("X");
        Word caller = Word.colon("CALLER", false, List.of(first));
        var dictionary = Dictionary.empty()
            .define(first)
            .define(caller)
            .define(primitive("X"));
        var found = dictionary.lookup("CALLER");
        var colon = (Word.Colon) found.orElseThrow();
        assertSame(first, colon.body().getFirst());
        assertEquals(1, colon.body().size());
    }

    private static Word primitive(String name) {
        return new Word.Primitive(
            name, false, machine -> {
        }
        );
    }
}
