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

    interface TextInputCallback {
        void onSubmitted(String text);
        void onCancelled();
    }

    /** True when a supported AnkiDroid provider is installed and discoverable. */
    boolean backendAvailable();

    /** Human-readable selected backend, e.g. "AnkiDroid Retry" or "AnkiDroid". */
    String backendName();

    /** True when the selected backend's database permission has already been granted. */
    boolean hasAccess();

    /**
     * Requests access when needed.
     *
     * @return true when access is already available; false when an Android
     * permission request was launched or access cannot currently be requested.
     */
    boolean requestAccess();

    /**
     * Opens a platform-native single-line text editor. On Android this uses a
     * real EditText so IME composition systems such as Vietnamese Telex work.
     */
    void requestTypedAnswer(
            String title,
            String prompt,
            String positiveLabel,
            String negativeLabel,
            TextInputCallback callback
    );

    /** Loads the next due card without blocking the game/render thread. */
    void loadNextCard(CardCallback callback);

    /** Submits a rating (Anki ease 1..4) without blocking the game/render thread. */
    void answer(StudyCard card, int ease, long timeTakenMs, AnswerCallback callback);
}
