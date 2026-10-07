package com.shatteredpixel.shatteredpixeldungeon.study;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudyPreviousRatingTest {

    @After
    public void cleanUp() {
        StudyPreviousRating.resetForTest();
    }

    @Test
    public void recordsValidEaseValues() {
        for (int ease = 1; ease <= 4; ease++) {
            StudyPreviousRating.record(ease);
            assertEquals(ease, StudyPreviousRating.lastEase());
        }
    }

    @Test
    public void ignoresInvalidEaseValues() {
        StudyPreviousRating.record(3);
        StudyPreviousRating.record(0);
        assertEquals(3, StudyPreviousRating.lastEase());
        StudyPreviousRating.record(5);
        assertEquals(3, StudyPreviousRating.lastEase());
    }
}
