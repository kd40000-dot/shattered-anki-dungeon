/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

/** Platform-neutral representation of one due Anki review. */
public final class StudyCard {

    public final long noteId;
    public final int ord;
    public final int reps;
    public final int buttonCount;
    public final String question;
    public final String answer;
    public final String[] nextReviewTimes;
    public final String[] mediaFiles;

    public StudyCard(
            long noteId,
            int ord,
            int reps,
            int buttonCount,
            String question,
            String answer,
            String[] nextReviewTimes,
            String[] mediaFiles
    ) {
        this.noteId = noteId;
        this.ord = ord;
        this.reps = reps;
        this.buttonCount = buttonCount;
        this.question = StudyTextSanitizer.question(question);
        this.answer = answer == null ? "" : answer;
        this.nextReviewTimes = StudyTextSanitizer.intervals(nextReviewTimes);
        this.mediaFiles = mediaFiles == null ? new String[0] : mediaFiles;
    }
}
