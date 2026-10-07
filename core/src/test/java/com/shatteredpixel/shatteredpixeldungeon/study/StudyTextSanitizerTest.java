package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudyTextSanitizerTest {

    @Test
    public void removesRenderedTypedAnswerMarker() {
        assertEquals(
                "wrong",
                StudyTextSanitizer.question("wrong\n[[type:Back]]")
        );
    }

    @Test
    public void removesCurlyTypedAnswerMarkerToo() {
        assertEquals(
                "sing, sings",
                StudyTextSanitizer.question("sing, sings {{type:Back}}")
        );
    }

    @Test
    public void stripsBidiFormattingCharactersFromIntervals() {
        assertEquals(
                "10m",
                StudyTextSanitizer.interval("\u206810m\u2069")
        );
        assertEquals(
                "1.3mo",
                StudyTextSanitizer.interval("\u20681.3\u2069mo")
        );
    }

    @Test
    public void stripsAlreadyReplacedFormattingGlyphsFromIntervals() {
        assertEquals(
                "14d",
                StudyTextSanitizer.interval("\uFFFD14\uFFFDd")
        );
    }

    @Test
    public void normalizesNonBreakingSpaces() {
        assertEquals(
                "29 d",
                StudyTextSanitizer.interval("29\u00A0d")
        );
    }
}
