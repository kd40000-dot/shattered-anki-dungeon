/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ConjuredRation;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndStudyRating;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.Game;

/**
 * Serialized Conjure Food review session with stale-callback protection.
 */
public final class ConjureFoodStudy {

    private static final StudySessionGuard.Owner OWNER = StudySessionGuard.Owner.CONJURE_FOOD;
    private static final String[] EASE_KEYS = {"again", "hard", "good", "easy"};

    private static long sessionToken;
    private static boolean sessionActive;
    private static boolean loading;
    private static boolean submitting;
    private static long cardShownAt;

    private ConjureFoodStudy() {
    }

    public static boolean sessionActive() {
        return sessionActive && isCurrent(sessionToken);
    }

    public static void reset() {
        if (StudySessionGuard.isCurrent(OWNER, sessionToken)) {
            StudySessionGuard.finish(OWNER, sessionToken);
        }
        clearLocalState();
    }

    public static void start() {
        if (!StudyRunState.conjureFoodEnabled()) {
            return;
        }

        if (Study.service == null || !Study.service.backendAvailable()) {
            showMessage(Messages.get(ConjureFoodStudy.class, "no_backend"));
            return;
        }

        if (!Study.service.hasAccess()) {
            // Do not create a gameplay session until Android permission handling
            // is completely finished.
            Study.service.requestAccess((granted, message) ->
                    showMessage(message == null ? "" : message));
            return;
        }

        if (CombatStudy.hasPendingRetry()) {
            showMessage(Messages.get(ConjureFoodStudy.class, "combat_retry_pending"));
            return;
        }

        if (StudySessionGuard.busy()) {
            showMessage(Messages.get(ConjureFoodStudy.class, "busy"));
            return;
        }

        long token = StudySessionGuard.begin(OWNER);
        if (token == 0L) return;

        sessionToken = token;
        sessionActive = true;
        loading = false;
        submitting = false;
        loadNextCard(token);
    }

    private static void loadNextCard(final long token) {
        if (!isCurrent(token) || loading || submitting) {
            return;
        }

        loading = true;
        Study.service.loadNextCard(new StudyService.CardCallback() {
            @Override
            public void onCardLoaded(StudyCard card) {
                if (!isCurrent(token)) return;
                loading = false;
                if (card == null) {
                    fail(token, Messages.get(ConjureFoodStudy.class, "load_error"));
                    return;
                }
                cardShownAt = Game.realTime;
                showQuestion(token, card);
            }

            @Override
            public void onNoCardsDue() {
                if (!isCurrent(token)) return;
                loading = false;
                finish(token);
                showMessage(Messages.get(ConjureFoodStudy.class, "no_cards"));
            }

            @Override
            public void onError(String message) {
                if (!isCurrent(token)) return;
                loading = false;
                fail(token, Messages.get(ConjureFoodStudy.class, "error", safe(message)));
            }
        });
    }

    private static void showQuestion(final long token, final StudyCard card) {
        if (!isCurrent(token)) return;

        int completed = StudyRunState.reviewsTowardNext();
        int required = StudyRunState.reviewsRequired();

        String progress = Messages.get(
                ConjureFoodStudy.class,
                "progress",
                completed,
                required
        );

        ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
                Messages.get(ConjureFoodStudy.class, "title"),
                card.question,
                "",
                512,
                false,
                Messages.get(ConjureFoodStudy.class, "check"),
                Messages.get(ConjureFoodStudy.class, "stop"),
                true,
                true,
                progress
        ) {
            @Override
            public void onSelect(boolean positive, String text) {
                if (!isCurrent(token)) return;
                if (!positive) {
                    cancel(token);
                    return;
                }
                showRating(token, card, text == null ? "" : text);
            }

            @Override
            public void onBackPressed() {
                cancel(token);
                hide();
            }

            @Override
            protected void onDismissed() {
                cancel(token);
            }
        });
    }

    private static void showRating(final long token, final StudyCard card, String typed) {
        if (!isCurrent(token)) return;

        String matched = TypedAnswerMatcher.matchedAlternative(typed, card.answer);
        boolean correct = matched != null;

        int count = Math.max(1, Math.min(4, card.buttonCount));
        String[] options = new String[count];

        for (int i = 0; i < count; i++) {
            String label = Messages.get(ConjureFoodStudy.class, EASE_KEYS[i]);
            String interval = i < card.nextReviewTimes.length ? card.nextReviewTimes[i] : "";
            options[i] = interval == null || interval.trim().isEmpty()
                    ? label
                    : label + "\n" + interval;
        }

        Study.service.playAnswerAudio(card, typed);

        ShatteredPixelDungeon.scene().addToFront(new WndStudyRating(
                correct
                        ? Messages.get(ConjureFoodStudy.class, "correct")
                        : Messages.get(ConjureFoodStudy.class, "incorrect"),
                correct ? ItemSlot.UPGRADED : ItemSlot.DEGRADED,
                card.question,
                typed,
                card.answer,
                false,
                Messages.get(ConjureFoodStudy.class, "you"),
                Messages.get(ConjureFoodStudy.class, "answer_label"),
                options
        ) {
            @Override
            protected void onSelect(int index) {
                if (!isCurrent(token)) return;
                submitRating(token, card, index + 1);
            }

            @Override
            protected void onCancelled() {
                cancel(token);
            }
        });
    }

    private static void submitRating(final long token, final StudyCard card, int ease) {
        if (!isCurrent(token) || submitting) return;
        submitting = true;

        long elapsed = Math.max(0L, Game.realTime - cardShownAt);

        Study.service.answer(card, ease, elapsed, new StudyService.AnswerCallback() {
            @Override
            public void onAnswered() {
                if (!isCurrent(token)) return;
                submitting = false;
                StudyPreviousRating.record(ease);

                boolean completedRation = StudyRunState.recordResolvedReview();
                persistProgressSafely();

                if (completedRation) {
                    finish(token);
                    grantRation();
                } else {
                    loadNextCard(token);
                }
            }

            @Override
            public void onError(String message) {
                if (!isCurrent(token)) return;
                submitting = false;
                fail(token, Messages.get(ConjureFoodStudy.class, "error", safe(message)));
            }
        });
    }

    private static void persistProgressSafely() {
        try {
            Dungeon.saveGame(GamesInProgress.curSlot);
        } catch (Exception e) {
            Game.reportException(e);
            GLog.w(Messages.get(ConjureFoodStudy.class, "save_warning"));
        }
    }

    private static void grantRation() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            GLog.w(Messages.get(ConjureFoodStudy.class, "reward_deferred"));
            return;
        }

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

    private static void fail(long token, String detail) {
        if (!isCurrent(token)) return;
        finish(token);
        showMessage(detail);
    }

    private static void cancel(long token) {
        if (!isCurrent(token)) return;
        finish(token);
    }

    private static void finish(long token) {
        if (!isCurrent(token)) return;
        StudySessionGuard.finish(OWNER, token);
        clearLocalState();
    }

    private static boolean isCurrent(long token) {
        return sessionActive
                && sessionToken == token
                && StudySessionGuard.isCurrent(OWNER, token);
    }

    private static void clearLocalState() {
        sessionToken = 0L;
        sessionActive = false;
        loading = false;
        submitting = false;
        cardShownAt = 0L;
    }

    private static void showMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        if (ShatteredPixelDungeon.scene() != null) {
            ShatteredPixelDungeon.scene().addToFront(new WndMessage(message));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
