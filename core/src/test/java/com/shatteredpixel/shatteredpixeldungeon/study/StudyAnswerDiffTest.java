package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StudyAnswerDiffTest {

    @Test
    public void closestAlternativeMatchesTypedVariant() {
        assertEquals("tập", StudyAnswerDiff.closestAlternative("tập", "luyện|tập"));
        assertEquals("luyện", StudyAnswerDiff.closestAlternative("luyẹn", "luyện|tập"));
    }

    @Test
    public void alignmentMarksMatchesAndSubstitutions() {
        List<StudyAnswerDiff.Piece> pieces = StudyAnswerDiff.align("tap", "tập");

        assertEquals(3, pieces.size());
        assertTrue(pieces.get(0).match);
        assertFalse(pieces.get(1).match);
        assertTrue(pieces.get(2).match);

        assertEquals(Character.valueOf('a'), pieces.get(1).typed);
        assertEquals(Character.valueOf('ậ'), pieces.get(1).expected);
    }

    @Test
    public void alignmentRepresentsMissingExpectedCharacters() {
        List<StudyAnswerDiff.Piece> pieces = StudyAnswerDiff.align("đ", "đỡ");

        assertEquals(2, pieces.size());
        assertTrue(pieces.get(0).match);
        assertNull(pieces.get(1).typed);
        assertEquals(Character.valueOf('ỡ'), pieces.get(1).expected);
        assertFalse(pieces.get(1).match);
    }

    @Test
    public void matchingIsCaseInsensitiveButAccentSensitive() {
        List<StudyAnswerDiff.Piece> caseOnly = StudyAnswerDiff.align("HỎI", "hỏi");
        for (StudyAnswerDiff.Piece piece : caseOnly) {
            assertTrue(piece.match);
        }

        List<StudyAnswerDiff.Piece> accentDifference = StudyAnswerDiff.align("hoi", "hỏi");
        assertFalse(accentDifference.get(1).match);
    }
}
