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

public interface StudyService {

    interface CardCallback {
        void onCardLoaded(StudyCard card);
        void onNoCardsDue();
        void onError(String message);
    }

    interface AnswerCallback {
        void onAnswered();
        void onError(String message);
    }

    interface AccessCallback {
        void onAccessResult(boolean granted, String message);
    }

    /** True when a supported AnkiDroid provider is installed and discoverable. */
    boolean backendAvailable();

    /** Human-readable selected backend, e.g. "AnkiDroid Retry" or "AnkiDroid". */
    String backendName();

    /** True when the selected backend's database permission has already been granted. */
    boolean hasAccess();

    /**
     * Requests provider access when needed. The callback is invoked only after
     * Android has produced a final permission result (or immediately when
     * access is already available/unavailable).
     *
     * @return true only when access is already available synchronously.
     */
    boolean requestAccess(AccessCallback callback);

    /** Loads the next due card without blocking the game/render thread. */
    void loadNextCard(CardCallback callback);

    /** Plays the revealed answer pronunciation, if the provider exposes audio. */
    void playAnswerAudio(StudyCard card, String typedAnswer);

    /** Submits a rating (Anki ease 1..4) without blocking the game/render thread. */
    void answer(StudyCard card, int ease, long timeTakenMs, AnswerCallback callback);
}
