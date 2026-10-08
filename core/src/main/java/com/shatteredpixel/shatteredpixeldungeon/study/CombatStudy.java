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
import com.watabou.utils.Callback;

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
    /** Stays true across every Wrong -> Again retry until this card is finally resolved. */
    private static boolean retryPenaltyActive;
    private static Result result;
    private static long attemptStartedAt;

    private CombatStudy() {
    }

    /**
     * @return true when this attack was intercepted by the study layer.
     */
    public static boolean interceptAttack(Hero hero, Char target) {
        StudyDiagnostics.mark("combat intercept ready=" + (hero != null && hero.ready)
                + " pendingRetry=" + (pendingRetryCard != null)
                + " guard=" + StudySessionGuard.owner()
                + " target=" + (target == null ? "null" : target.getClass().getSimpleName() + "@" + target.pos));
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
            StudyDiagnostics.mark("combat restoring retry card note=" + pendingRetryCard.noteId
                    + " ord=" + pendingRetryCard.ord + " token=" + token
                    + " forceAgain=" + retryPenaltyActive);
            card = pendingRetryCard;
            pendingRetryCard = null;
            currentAttemptIsRetry = retryPenaltyActive;
            showQuestion(token);
        } else {
            currentAttemptIsRetry = false;
            retryPenaltyActive = false;
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
        retryPenaltyActive = false;
    }

    private static void loadNextCard(final long token) {
        if (!isCurrent(token)) {
            return;
        }

        loading = true;
        StudyDiagnostics.mark("combat loadNextCard token=" + token);
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
                StudyDiagnostics.mark("combat card loaded note=" + loaded.noteId + " ord=" + loaded.ord
                        + " reps=" + loaded.reps + " token=" + token);
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

    /**
     * Study windows must only be constructed on SPD's render thread.
     * New cards already arrive there through AndroidAnkiStudyService, but a
     * cached Wrong -> Again retry is reached directly from Hero.act() on the
     * SHPD Actor Thread. Always marshal here so both paths are safe.
     */
    private static void showQuestion(final long token) {
        Game.runOnRenderThread(new Callback() {
            @Override
            public void call() {
                showQuestionOnRenderThread(token);
            }
        });
    }

    private static void showQuestionOnRenderThread(final long token) {
        if (!isCurrent(token) || pendingHero == null || pendingTarget == null || card == null) {
            cancelSession(token);
            return;
        }

        result = null;
        attemptStartedAt = Game.realTime;
        final StudyCard shownCard = card;
        StudyDiagnostics.mark("combat showQuestion(render) retry=" + currentAttemptIsRetry
                + " note=" + shownCard.noteId + " token=" + token);

        ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
                Messages.get(CombatStudy.class, "title"),
                shownCard.question,
                "",
                512,
                false,
                Messages.get(CombatStudy.class, "check"),
                Messages.get(CombatStudy.class, "cancel"),
                true,
                true,
                null
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

        StudyDiagnostics.mark("combat reveal typedLen=" + trimmed.length()
                + " retry=" + currentAttemptIsRetry + " note=" + shownCard.noteId
                + " token=" + token);

        if (trimmed.isEmpty() && shownCard.reps == 0 && !currentAttemptIsRetry) {
            result = Result.NEW_BLANK;
        } else if (matched != null) {
            result = Result.CORRECT;
        } else {
            result = Result.WRONG;
        }

        String title;
        int titleColor;

        if (result == Result.CORRECT) {
            title = Messages.get(CombatStudy.class, "correct");
            titleColor = ItemSlot.UPGRADED;
        } else if (result == Result.NEW_BLANK) {
            title = Messages.get(CombatStudy.class, "revealed");
            titleColor = ItemSlot.ENHANCED;
        } else {
            title = Messages.get(CombatStudy.class, "incorrect");
            titleColor = ItemSlot.DEGRADED;
        }

        int count = Math.max(1, Math.min(4, shownCard.buttonCount));
        String[] options = new String[count];
        final boolean correctRetry = result == Result.CORRECT && retryPenaltyActive;
        for (int i = 0; i < count; i++) {
            String label = Messages.get(CombatStudy.class, EASE_KEYS[i]);
            // Match the Andor integration: once a card has been missed and is
            // finally answered correctly, every visible option shows the Again
            // interval because Again is the rating that will actually be saved.
            int intervalIndex = correctRetry ? 0 : i;
            String interval = intervalIndex < shownCard.nextReviewTimes.length
                    ? shownCard.nextReviewTimes[intervalIndex]
                    : "";
            options[i] = interval == null || interval.trim().isEmpty()
                    ? label
                    : label + "\n" + interval;
        }

        ShatteredPixelDungeon.scene().addToFront(new WndStudyRating(
                title,
                titleColor,
                shownCard.question,
                typed,
                shownCard.answer,
                result == Result.NEW_BLANK,
                Messages.get(CombatStudy.class, "you"),
                Messages.get(CombatStudy.class, "answer_label"),
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
            StudyDiagnostics.mark("combat wrong->Again note=" + shownCard.noteId
                    + " token=" + token + " heroReady=" + pendingHero.ready);
            Hero hero = pendingHero;

            // Once a card is missed, keep the Again penalty alive across every
            // local retry until the review is actually accepted by Anki.
            retryPenaltyActive = true;

            // Preserve only immutable card data. The current study session,
            // token, windows, and IME ownership are fully ended before the
            // skipped combat turn is released to SPD.
            pendingRetryCard = copyCard(shownCard);
            finishSession(token, true);
            StudyDiagnostics.mark("combat Again session finished; retry detached="
                    + (pendingRetryCard != null));

            if (hero != null) {
                hero.studyAttackFailed();
            }
            return;
        }

        submitting = true;
        StudyDiagnostics.mark("combat submit ease=" + pressedEase
                + " retry=" + currentAttemptIsRetry + " result=" + result
                + " note=" + shownCard.noteId + " token=" + token);

        final int effectiveEase =
                result == Result.CORRECT && retryPenaltyActive ? 1 : pressedEase;
        final boolean shouldAttack = result == Result.CORRECT;
        final Hero hero = pendingHero;
        final Char target = pendingTarget;
        final long timeTaken = Math.max(0L, Game.realTime - attemptStartedAt);

        Study.service.answer(shownCard, effectiveEase, timeTaken, new StudyService.AnswerCallback() {
            @Override
            public void onAnswered() {
                if (!isCurrent(token) || card != shownCard) return;

                submitting = false;
                StudyPreviousRating.record(effectiveEase);
                StudyDiagnostics.mark("combat answer saved note=" + shownCard.noteId
                        + " shouldAttack=" + shouldAttack + " token=" + token);
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
        StudyDiagnostics.mark("combat finishSession token=" + token
                + " preserveRetry=" + preserveRetryCard);
        StudySessionGuard.finish(OWNER, token);
        clearSessionState(true);
        if (!preserveRetryCard) {
            pendingRetryCard = null;
            retryPenaltyActive = false;
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

    private static void showMessage(final String message) {
        if (message == null || message.trim().isEmpty()) return;
        Game.runOnRenderThread(new Callback() {
            @Override
            public void call() {
                if (ShatteredPixelDungeon.scene() != null) {
                    ShatteredPixelDungeon.scene().addToFront(new WndMessage(message));
                }
            }
        });
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
