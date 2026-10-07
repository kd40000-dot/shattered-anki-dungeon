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

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ConjuredRation;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndStudyRating;
import com.watabou.noosa.Game;

/**
 * Native-SPD UI flow for the Conjure Food review session.
 *
 * Provider I/O is delegated to StudyService, so this class has no Android or
 * AnkiDroid package dependencies.
 */
public final class ConjureFoodStudy {

    private static final String[] EASE_KEYS = {"again", "hard", "good", "easy"};

    private static boolean sessionActive;
    private static long cardShownAt;

    private ConjureFoodStudy() {
    }

    public static boolean sessionActive() {
        return sessionActive;
    }

    public static void start() {
        if (sessionActive || !StudyRunState.conjureFoodEnabled()) {
            return;
        }

        if (Study.service == null || !Study.service.backendAvailable()) {
            showMessage(Messages.get(ConjureFoodStudy.class, "no_backend"));
            return;
        }

        if (!Study.service.hasAccess()) {
            Study.service.requestAccess();
            showMessage(Messages.get(ConjureFoodStudy.class, "permission"));
            return;
        }

        sessionActive = true;
        loadNextCard();
    }

    private static void loadNextCard() {
        if (!sessionActive) {
            return;
        }

        Study.service.loadNextCard(new StudyService.CardCallback() {
            @Override
            public void onCardLoaded(StudyCard card) {
                if (!sessionActive) {
                    return;
                }
                cardShownAt = Game.realTime;
                showQuestion(card);
            }

            @Override
            public void onNoCardsDue() {
                sessionActive = false;
                showMessage(Messages.get(ConjureFoodStudy.class, "no_cards"));
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private static void showQuestion(final StudyCard card) {
        int completed = StudyRunState.reviewsTowardNext();
        int required = StudyRunState.reviewsRequired();

        String prompt = Messages.get(
                ConjureFoodStudy.class,
                "prompt",
                completed,
                required,
                card.question
        );

        Study.service.requestTypedAnswer(
                Messages.get(ConjureFoodStudy.class, "title"),
                prompt,
                Messages.get(ConjureFoodStudy.class, "check"),
                Messages.get(ConjureFoodStudy.class, "stop"),
                new StudyService.TextInputCallback() {
                    @Override
                    public void onSubmitted(String text) {
                        if (!sessionActive) return;
                        showRating(card, text == null ? "" : text);
                    }

                    @Override
                    public void onCancelled() {
                        sessionActive = false;
                    }
                }
        );
    }

    private static void showRating(final StudyCard card, String typed) {
        String matched = TypedAnswerMatcher.matchedAlternative(typed, card.answer);
        boolean correct = matched != null;

        String shownTyped = typed.trim().isEmpty()
                ? Messages.get(ConjureFoodStudy.class, "blank_answer")
                : typed.trim();

        String result = correct
                ? Messages.get(ConjureFoodStudy.class, "result_correct", shownTyped, matched)
                : Messages.get(ConjureFoodStudy.class, "result_incorrect", shownTyped, card.answer);

        int count = Math.max(1, Math.min(4, card.buttonCount));
        String[] options = new String[count];

        for (int i = 0; i < count; i++) {
            String label = Messages.get(ConjureFoodStudy.class, EASE_KEYS[i]);
            String interval = i < card.nextReviewTimes.length ? card.nextReviewTimes[i] : "";
            options[i] = interval == null || interval.trim().isEmpty()
                    ? label
                    : label + "\n" + interval;
        }

        ShatteredPixelDungeon.scene().addToFront(new WndStudyRating(
                correct
                        ? Messages.get(ConjureFoodStudy.class, "correct")
                        : Messages.get(ConjureFoodStudy.class, "incorrect"),
                correct ? ItemSlot.UPGRADED : ItemSlot.DEGRADED,
                result,
                options
        ) {
            @Override
            protected void onSelect(int index) {
                submitRating(card, index + 1);
            }

            @Override
            protected void onCancelled() {
                sessionActive = false;
            }
        });
    }

    private static void submitRating(final StudyCard card, int ease) {
        long elapsed = Math.max(0L, Game.realTime - cardShownAt);

        Study.service.answer(card, ease, elapsed, new StudyService.AnswerCallback() {
            @Override
            public void onAnswered() {
                boolean completedRation = StudyRunState.recordResolvedReview();

                // Persist study progression immediately. SPD can reload the run when
                // changing scenes/floors, so progression must never depend on a later autosave.
                Dungeon.saveGame(GamesInProgress.curSlot);

                if (completedRation) {
                    sessionActive = false;
                    grantRation();
                } else {
                    loadNextCard();
                }
            }

            @Override
            public void onError(String message) {
                fail(message);
            }
        });
    }

    private static void grantRation() {
        ConjuredRation ration = new ConjuredRation();
        boolean collected = ration.collect();

        String message;
        if (collected) {
            message = Messages.get(
                    ConjureFoodStudy.class,
                    "conjured",
                    StudyRunState.reviewsRequired()
            );
        } else {
            Dungeon.level.drop(ration, Dungeon.hero.pos).sprite.drop();
            message = Messages.get(
                    ConjureFoodStudy.class,
                    "conjured_drop",
                    StudyRunState.reviewsRequired()
            );
        }

        GLog.p(message);
        showMessage(message);
    }

    private static void fail(String detail) {
        sessionActive = false;
        showMessage(Messages.get(
                ConjureFoodStudy.class,
                "error",
                detail == null ? "" : detail
        ));
    }

    private static void showMessage(String message) {
        if (ShatteredPixelDungeon.scene() != null) {
            ShatteredPixelDungeon.scene().addToFront(new WndMessage(message));
        }
    }
}
