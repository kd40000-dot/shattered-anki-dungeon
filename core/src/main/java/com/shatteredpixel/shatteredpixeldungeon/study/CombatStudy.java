/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndStudyRating;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.watabou.noosa.Game;

/**
 * Gates normal hero attacks behind one serialized Anki review session.
 *
 * Every async callback carries a session token. If the player cancels, changes
 * scene, starts another study action, or a newer session supersedes this one,
 * stale callbacks become no-ops and can never trigger an attack.
 */
public final class CombatStudy {

    private enum Result {
        CORRECT,
        WRONG,
        NEW_BLANK
    }

    private static final StudySessionGuard.Owner OWNER = StudySessionGuard.Owner.COMBAT;
    private static final String[] EASE_KEYS = {"again", "hard", "good", "easy"};

    private static long sessionToken;
    private static Hero pendingHero;
    private static Char pendingTarget;
    private static StudyCard card;
    /** Card intentionally left unresolved by Wrong -> Again. Not tied to any live session. */
    private static StudyCard pendingRetryCard;
    private static boolean loading;
    private static boolean submitting;
    private static boolean currentAttemptIsRetry;
    private static Result result;
    private static long attemptStartedAt;

    private CombatStudy() {
    }

    /**
     * @return true when this attack was intercepted by the study layer.
     */
    public static boolean interceptAttack(Hero hero, Char target) {
        if (!StudyRunState.combatEnabled()) {
            return false;
        }

        if (hero == null || target == null) {
            return false;
        }

        if (Study.service == null || !Study.service.backendAvailable()) {
            if (StudySessionGuard.isCurrent(OWNER, sessionToken)) {
                finishSession(sessionToken, false);
            }
            pendingRetryCard = null;
            hero.studyAttackCancelled();
            showMessage(Messages.get(CombatStudy.class, "no_backend"));
            return true;
        }

        if (!Study.service.hasAccess()) {
            // No gameplay action or unresolved retry is kept alive across
            // Android's permission UI.
            if (StudySessionGuard.isCurrent(OWNER, sessionToken)) {
                finishSession(sessionToken, false);
            }
            pendingRetryCard = null;
            hero.studyAttackCancelled();
            Study.service.requestAccess((granted, message) ->
                    showMessage(message == null ? "" : message));
            return true;
        }

        if (StudySessionGuard.isCurrent(OWNER, sessionToken)) {
            // A live combat review never survives across turns anymore. Any
            // re-entry while it is live is duplicate input and is cancelled.
            hero.studyAttackCancelled();
            return true;
        }

        if (StudySessionGuard.busy()) {
            hero.studyAttackCancelled();
            showMessage(Messages.get(CombatStudy.class, "busy"));
            return true;
        }

        long token = StudySessionGuard.begin(OWNER);
        if (token == 0L) {
            hero.studyAttackCancelled();
            return true;
        }

        clearSessionState(false);
        sessionToken = token;
        pendingHero = hero;
        pendingTarget = target;

        if (pendingRetryCard != null) {
            card = pendingRetryCard;
            pendingRetryCard = null;
            currentAttemptIsRetry = true;
            showQuestion(token);
        } else {
            currentAttemptIsRetry = false;
            loadNextCard(token);
        }
        return true;
    }

    public static void reset() {
        if (StudySessionGuard.isCurrent(OWNER, sessionToken)) {
            StudySessionGuard.finish(OWNER, sessionToken);
        }
        clearSessionState(true);
        pendingRetryCard = null;
    }

    private static void loadNextCard(final long token) {
        if (!isCurrent(token)) {
            return;
        }

        loading = true;
        Study.service.loadNextCard(new StudyService.CardCallback() {
            @Override
            public void onCardLoaded(StudyCard loaded) {
                if (!isCurrent(token)) return;
                loading = false;
                if (loaded == null) {
                    failSession(token, Messages.get(CombatStudy.class, "load_error"));
                    return;
                }
                card = loaded;
                showQuestion(token);
            }

            @Override
            public void onNoCardsDue() {
                if (!isCurrent(token)) return;
                loading = false;
                approvePendingAttack(token);
            }

            @Override
            public void onError(String message) {
                if (!isCurrent(token)) return;
                loading = false;
                failSession(token, Messages.get(CombatStudy.class, "provider_error", safe(message)));
            }
        });
    }

    private static void showQuestion(final long token) {
        if (!isCurrent(token) || pendingHero == null || pendingTarget == null || card == null) {
            cancelSession(token);
            return;
        }

        result = null;
        attemptStartedAt = Game.realTime;
        final StudyCard shownCard = card;

        ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
                Messages.get(CombatStudy.class, "title"),
                shownCard.question,
                "",
                512,
                false,
                Messages.get(CombatStudy.class, "check"),
                Messages.get(CombatStudy.class, "cancel"),
                true
        ) {
            @Override
            public void onSelect(boolean positive, String text) {
                if (!isCurrent(token) || card != shownCard) return;
                if (!positive) {
                    cancelSession(token);
                    return;
                }
                revealAnswer(token, shownCard, text == null ? "" : text);
            }

            @Override
            public void onBackPressed() {
                cancelSession(token);
                hide();
            }

            @Override
            protected void onDismissed() {
                cancelSession(token);
            }
        });
    }

    private static void revealAnswer(final long token, final StudyCard shownCard, String typed) {
        if (!isCurrent(token) || card != shownCard) return;

        String trimmed = typed.trim();
        String matched = TypedAnswerMatcher.matchedAlternative(trimmed, shownCard.answer);

        if (trimmed.isEmpty() && shownCard.reps == 0 && !currentAttemptIsRetry) {
            result = Result.NEW_BLANK;
        } else if (matched != null) {
            result = Result.CORRECT;
        } else {
            result = Result.WRONG;
        }

        String shownTyped = trimmed.isEmpty()
                ? Messages.get(CombatStudy.class, "blank_answer")
                : trimmed;

        String body;
        String title;
        int titleColor;

        if (result == Result.CORRECT) {
            title = Messages.get(CombatStudy.class, "correct");
            titleColor = ItemSlot.UPGRADED;
            body = Messages.get(CombatStudy.class, "result_correct", shownTyped, matched);
        } else if (result == Result.NEW_BLANK) {
            title = Messages.get(CombatStudy.class, "revealed");
            titleColor = ItemSlot.ENHANCED;
            body = Messages.get(CombatStudy.class, "result_revealed", shownCard.answer);
        } else {
            title = Messages.get(CombatStudy.class, "incorrect");
            titleColor = ItemSlot.DEGRADED;
            body = Messages.get(CombatStudy.class, "result_incorrect", shownTyped, shownCard.answer);
        }

        int count = Math.max(1, Math.min(4, shownCard.buttonCount));
        String[] options = new String[count];
        for (int i = 0; i < count; i++) {
            String label = Messages.get(CombatStudy.class, EASE_KEYS[i]);
            String interval = i < shownCard.nextReviewTimes.length ? shownCard.nextReviewTimes[i] : "";
            options[i] = interval == null || interval.trim().isEmpty()
                    ? label
                    : label + "\n" + interval;
        }

        ShatteredPixelDungeon.scene().addToFront(new WndStudyRating(
                title,
                titleColor,
                body,
                options
        ) {
            @Override
            protected void onSelect(int index) {
                if (!isCurrent(token) || card != shownCard) return;
                chooseEase(token, shownCard, index + 1);
            }

            @Override
            protected void onCancelled() {
                cancelSession(token);
            }
        });
    }

    private static void chooseEase(final long token, final StudyCard shownCard, int pressedEase) {
        if (!isCurrent(token) || card != shownCard || result == null || pendingHero == null) {
            cancelSession(token);
            return;
        }

        if (result == Result.WRONG && pressedEase == 1) {
            Hero hero = pendingHero;

            // Preserve only immutable card data. The current study session,
            // token, windows, and IME ownership are fully ended before the
            // skipped combat turn is released to SPD.
            pendingRetryCard = copyCard(shownCard);
            finishSession(token, true);

            if (hero != null) {
                hero.studyAttackFailed();
            }
            return;
        }

        submitting = true;

        final int effectiveEase =
                result == Result.CORRECT && currentAttemptIsRetry ? 1 : pressedEase;
        final boolean shouldAttack = result == Result.CORRECT;
        final Hero hero = pendingHero;
        final Char target = pendingTarget;
        final long timeTaken = Math.max(0L, Game.realTime - attemptStartedAt);

        Study.service.answer(shownCard, effectiveEase, timeTaken, new StudyService.AnswerCallback() {
            @Override
            public void onAnswered() {
                if (!isCurrent(token) || card != shownCard) return;

                submitting = false;
                finishSession(token, false);

                if (hero == null) return;
                if (shouldAttack) {
                    hero.studyAttackApproved(target);
                } else {
                    hero.studyAttackFailed();
                }
            }

            @Override
            public void onError(String message) {
                if (!isCurrent(token)) return;
                submitting = false;
                failSession(token, Messages.get(CombatStudy.class, "save_error", safe(message)));
            }
        });
    }

    private static void approvePendingAttack(long token) {
        if (!isCurrent(token)) return;

        Hero hero = pendingHero;
        Char target = pendingTarget;
        finishSession(token, false);

        if (hero != null) {
            hero.studyAttackApproved(target);
        }
    }

    private static void failSession(long token, String message) {
        if (!isCurrent(token)) return;
        Hero hero = pendingHero;
        finishSession(token, false);
        if (hero != null) {
            hero.studyAttackCancelled();
        }
        showMessage(message);
    }

    private static void cancelSession(long token) {
        if (!isCurrent(token)) return;
        Hero hero = pendingHero;
        finishSession(token, false);
        if (hero != null) {
            hero.studyAttackCancelled();
        }
    }

    private static void finishSession(long token, boolean preserveRetryCard) {
        if (!isCurrent(token)) return;
        StudySessionGuard.finish(OWNER, token);
        clearSessionState(true);
        if (!preserveRetryCard) {
            pendingRetryCard = null;
        }
    }

    private static boolean isCurrent(long token) {
        return StudySessionGuard.isCurrent(OWNER, token) && sessionToken == token;
    }

    private static void clearSessionState(boolean clearToken) {
        pendingHero = null;
        pendingTarget = null;
        card = null;
        loading = false;
        submitting = false;
        currentAttemptIsRetry = false;
        result = null;
        attemptStartedAt = 0L;
        if (clearToken) {
            sessionToken = 0L;
        }
    }

    public static boolean hasPendingRetry() {
        return pendingRetryCard != null;
    }

    private static StudyCard copyCard(StudyCard source) {
        return new StudyCard(
                source.noteId,
                source.ord,
                source.reps,
                source.buttonCount,
                source.question,
                source.answer,
                source.nextReviewTimes.clone(),
                source.mediaFiles.clone()
        );
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
