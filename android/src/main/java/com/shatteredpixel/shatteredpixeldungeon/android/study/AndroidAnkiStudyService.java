/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.android.study;

import android.app.Activity;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyCard;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android implementation of the core study service, backed by AnkiDroid. */
public final class AndroidAnkiStudyService implements StudyService {

    public static final int REQUEST_ANKI_DATABASE_PERMISSION = 0x5341;

    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public AndroidAnkiStudyService(Activity activity) {
        this.activity = activity;
    }

    private AndroidAnkiBridge connect() {
        return AndroidAnkiBridge.connect(activity);
    }

    @Override
    public boolean backendAvailable() {
        return AndroidAnkiBridge.discoverBackend(activity) != null;
    }

    @Override
    public String backendName() {
        AndroidAnkiBridge.BackendInfo backend = AndroidAnkiBridge.discoverBackend(activity);
        if (backend == null) {
            return "Unavailable";
        }
        return backend.kind == AndroidAnkiBridge.BackendKind.RETRY
                ? "AnkiDroid Retry"
                : "AnkiDroid";
    }

    @Override
    public boolean hasAccess() {
        AndroidAnkiBridge bridge = connect();
        return bridge != null && bridge.hasPermission();
    }

    @Override
    public boolean requestAccess() {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            return false;
        }
        if (bridge.hasPermission()) {
            return true;
        }

        bridge.requestPermission(activity, REQUEST_ANKI_DATABASE_PERMISSION);
        return false;
    }

    @Override
    public void loadNextCard(CardCallback callback) {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            post(() -> callback.onError("No compatible AnkiDroid installation was found."));
            return;
        }
        if (!bridge.hasPermission()) {
            post(() -> callback.onError("AnkiDroid database access has not been granted."));
            return;
        }

        worker.execute(() -> {
            try {
                AndroidAnkiBridge.ReviewCard card = bridge.loadNextCard();
                if (card == null) {
                    post(callback::onNoCardsDue);
                    return;
                }

                StudyCard studyCard = new StudyCard(
                        card.noteId,
                        card.ord,
                        card.reps,
                        card.buttonCount,
                        card.question,
                        card.answer,
                        card.nextReviewTimes,
                        card.mediaFiles
                );
                post(() -> callback.onCardLoaded(studyCard));
            } catch (Exception e) {
                post(() -> callback.onError(errorMessage(e)));
            }
        });
    }

    @Override
    public void answer(StudyCard card, int ease, long timeTakenMs, AnswerCallback callback) {
        if (card == null) {
            post(() -> callback.onError("No review card is active."));
            return;
        }

        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            post(() -> callback.onError("No compatible AnkiDroid installation was found."));
            return;
        }
        if (!bridge.hasPermission()) {
            post(() -> callback.onError("AnkiDroid database access has not been granted."));
            return;
        }

        worker.execute(() -> {
            try {
                if (bridge.answer(card.noteId, card.ord, ease, timeTakenMs)) {
                    post(callback::onAnswered);
                } else {
                    post(() -> callback.onError("AnkiDroid did not accept the review result."));
                }
            } catch (Exception e) {
                post(() -> callback.onError(errorMessage(e)));
            }
        });
    }

    private static void post(Runnable runnable) {
        if (Gdx.app != null) {
            Gdx.app.postRunnable(runnable);
        } else {
            runnable.run();
        }
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }
}
