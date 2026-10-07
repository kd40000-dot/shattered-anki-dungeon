/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

/**
 * Mirrors AnkiDroid's previous-answer indicator.
 *
 * This is deliberately process/session state rather than run state: it tracks
 * the last review successfully recorded by Anki, regardless of whether that
 * review came from Combat or Conjure Food.
 */
public final class StudyPreviousRating {

    private static volatile int lastEase;

    private StudyPreviousRating() {
    }

    public static int lastEase() {
        return lastEase;
    }

    public static void record(int ease) {
        if (ease >= 1 && ease <= 4) {
            lastEase = ease;
        }
    }

    static void resetForTest() {
        lastEase = 0;
    }
}
