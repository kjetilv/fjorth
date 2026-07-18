package com.github.kjetilv.fjorth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryTest {

    private static Word primitive(String name) {
        return new Word.Primitive(name, false, machine -> {
        });
    }

    @Test
    void emptyDictionaryFindsNothing() {
        assertTrue(Dictionary.empty().lookup("DUP").isEmpty());
    }

    @Test
    void definedWordIsFound() {
        Word dup = primitive("DUP");
        Dictionary dictionary = Dictionary.empty().define(dup);
        assertSame(dup, dictionary.lookup("DUP").orElseThrow());
    }

    @Test
    void lookupIsCaseInsensitive() {
        Word dup = primitive("DUP");
        Dictionary dictionary = Dictionary.empty().define(dup);
        assertSame(dup, dictionary.lookup("dup").orElseThrow());
        assertSame(dup, dictionary.lookup("Dup").orElseThrow());
    }

    @Test
    void redefinitionShadowsOlderWord() {
        Word first = primitive("X");
        Word second = primitive("X");
        Dictionary dictionary = Dictionary.empty().define(first).define(second);
        assertSame(second, dictionary.lookup("X").orElseThrow());
    }

    @Test
    void defineDoesNotMutateOriginal() {
        Dictionary base = Dictionary.empty();
        Dictionary extended = base.define(primitive("DUP"));
        assertTrue(base.lookup("DUP").isEmpty());
        assertTrue(extended.lookup("DUP").isPresent());
    }

    @Test
    void compiledReferenceSurvivesRedefinition() {
        Word first = primitive("X");
        Word caller =  Word.colon("CALLER", false, List.of(first));
        Dictionary dictionary = Dictionary.empty()
            .define(first)
            .define(caller)
            .define(primitive("X"));
        Optional<Word> found = dictionary.lookup("CALLER");
        Word.Colon colon = (Word.Colon) found.orElseThrow();
        assertSame(first, colon.body().getFirst());
        assertEquals(1, colon.body().size());
    }
}
