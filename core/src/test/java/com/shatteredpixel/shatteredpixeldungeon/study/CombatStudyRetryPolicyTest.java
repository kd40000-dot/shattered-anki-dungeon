package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CombatStudyRetryPolicyTest {

    @Test
    public void correctRetryAlwaysRecordsAgain() {
        assertEquals(1, CombatStudy.resolvedEase(true, true, 1));
        assertEquals(1, CombatStudy.resolvedEase(true, true, 2));
        assertEquals(1, CombatStudy.resolvedEase(true, true, 3));
        assertEquals(1, CombatStudy.resolvedEase(true, true, 4));
    }

    @Test
    public void nonRetryUsesPressedEase() {
        assertEquals(1, CombatStudy.resolvedEase(true, false, 1));
        assertEquals(2, CombatStudy.resolvedEase(true, false, 2));
        assertEquals(3, CombatStudy.resolvedEase(true, false, 3));
        assertEquals(4, CombatStudy.resolvedEase(true, false, 4));
    }

    @Test
    public void correctRetryShowsAgainIntervalOnEveryButton() {
        for (int i = 0; i < 4; i++) {
            assertEquals(0, CombatStudy.displayIntervalIndex(true, i));
        }
    }

    @Test
    public void normalReviewShowsEachButtonsOwnInterval() {
        for (int i = 0; i < 4; i++) {
            assertEquals(i, CombatStudy.displayIntervalIndex(false, i));
        }
    }
}
