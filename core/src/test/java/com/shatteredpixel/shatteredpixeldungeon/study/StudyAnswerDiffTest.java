package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class StudyAnswerDiffTest {

    @Test
    public void choosesClosestPipeSeparatedAlternative() {
        assertEquals(
                "tập",
                StudyAnswerDiff.closestAlternative("tạp", "luyện|tập|thực hành")
        );
    }

    @Test
    public void exactAnswerProducesOnlyMatches() {
        List<StudyAnswerDiff.Piece> pieces =
                StudyAnswerDiff.alignToClosestAlternative("đỡ", "đỡ|giúp");

        assertFalse(pieces.isEmpty());
        for (StudyAnswerDiff.Piece piece : pieces) {
            assertTrue(piece.match);
            assertNotNull(piece.typed);
            assertNotNull(piece.expected);
        }
    }

    @Test
    public void substitutionMarksTypedAndExpectedAsDifferent() {
        List<StudyAnswerDiff.Piece> pieces =
                StudyAnswerDiff.alignToClosestAlternative("tap", "tập");

        boolean foundDifference = false;
        for (StudyAnswerDiff.Piece piece : pieces) {
            if (!piece.match) {
                foundDifference = true;
                assertNotNull(piece.typed);
                assertNotNull(piece.expected);
            }
        }
        assertTrue(foundDifference);
    }

    @Test
    public void missingCharacterCreatesExpectedOnlyPiece() {
        List<StudyAnswerDiff.Piece> pieces =
                StudyAnswerDiff.alignToClosestAlternative("tậ", "tập");

        boolean foundMissing = false;
        for (StudyAnswerDiff.Piece piece : pieces) {
            if (piece.typed == null && piece.expected != null) {
                foundMissing = true;
            }
        }
        assertTrue(foundMissing);
    }

    @Test
    public void comparisonRemainsCaseInsensitiveButAccentSensitive() {
        assertEquals(0, StudyAnswerDiff.editDistance("HỎI", "hỏi"));
        assertTrue(StudyAnswerDiff.editDistance("hoi", "hỏi") > 0);
    }
}
