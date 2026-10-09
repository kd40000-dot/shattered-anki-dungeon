package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class StudyIntegrationStateTest {

    @After
    public void cleanUp() {
        StudySessionGuard.reset();
        StudyRunState.reset(true, true);
    }

    @Test
    public void staleSessionTokensCannotBecomeCurrentAgain() {
        long combat = StudySessionGuard.begin(StudySessionGuard.Owner.COMBAT);
        assertTrue(combat > 0);
        assertTrue(StudySessionGuard.isCurrent(StudySessionGuard.Owner.COMBAT, combat));

        StudySessionGuard.finish(StudySessionGuard.Owner.COMBAT, combat);
        assertFalse(StudySessionGuard.isCurrent(StudySessionGuard.Owner.COMBAT, combat));

        long food = StudySessionGuard.begin(StudySessionGuard.Owner.CONJURE_FOOD);
        assertTrue(food > combat);
        assertTrue(StudySessionGuard.isCurrent(StudySessionGuard.Owner.CONJURE_FOOD, food));
        assertFalse(StudySessionGuard.isCurrent(StudySessionGuard.Owner.COMBAT, combat));
    }

    @Test
    public void onlyOneStudyOwnerCanRunAtATime() {
        long combat = StudySessionGuard.begin(StudySessionGuard.Owner.COMBAT);
        assertTrue(combat > 0);
        assertEquals(0L, StudySessionGuard.begin(StudySessionGuard.Owner.CONJURE_FOOD));
        assertEquals(StudySessionGuard.Owner.COMBAT, StudySessionGuard.owner());
    }

    @Test
    public void conjureFoodCostProgressesAndCapsAtFive() {
        StudyRunState.reset(true, true);

        int[] expected = {1,2,3,4,5,5,5,5,5,5,5};
        for (int cost : expected) {
            assertEquals(cost, StudyRunState.reviewsRequired());
            for (int i = 1; i < cost; i++) {
                assertFalse(StudyRunState.recordResolvedReview());
            }
            assertTrue(StudyRunState.recordResolvedReview());
        }

        assertEquals(5, StudyRunState.reviewsRequired());
    }

    @Test
    public void disabledConjureFoodDoesNotAdvance() {
        StudyRunState.reset(false, true);
        assertFalse(StudyRunState.recordResolvedReview());
        assertEquals(0, StudyRunState.conjuredRations());
        assertEquals(0, StudyRunState.reviewsTowardNext());
    }
}
