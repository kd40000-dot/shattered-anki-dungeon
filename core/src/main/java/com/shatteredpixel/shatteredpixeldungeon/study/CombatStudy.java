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
 * Gates normal hero attacks behind Anki reviews.
 *
 * Rules mirror the existing Andor's Trail integration:
 * - Correct first try: submit the selected ease, then attack.
 * - Wrong + Hard/Good/Easy: submit that ease and lose the attack turn.
 * - Wrong + Again: do not submit yet, lose the attack turn, and keep the same
 *   card for the next attack.
 * - Correct after a retry: submit Again regardless of the pressed rating, then attack.
 * - Blank on a brand-new card: reveal/rate it, but it does not earn an attack.
 */
public final class CombatStudy {

    private enum Result {
        CORRECT,
        WRONG,
        NEW_BLANK
    }

    private static final String[] EASE_KEYS = {"again", "hard", "good", "easy"};

    private static Hero pendingHero;
    private static Char pendingTarget;
    private static StudyCard card;
    private static boolean loading;
    private static boolean retryActive;
    private static Result result;
    private static long attemptStartedAt;

    private CombatStudy() {
    }

    /**
     * @return true when this attack has been intercepted and will be resolved
     * asynchronously by the study flow.
     */
    public static boolean interceptAttack(Hero hero, Char target) {
        if (!StudyRunState.combatEnabled()) {
            return false;
        }

        if (Study.service == null || !Study.service.backendAvailable()) {
            return false;
        }

        pendingHero = hero;
        pendingTarget = target;

        if (!Study.service.hasAccess()) {
            Study.service.requestAccess();
            showMessage(Messages.get(CombatStudy.class, "permission"));
            cancelPendingAttack();
            return true;
        }

        if (card != null) {
            showQuestion();
            return true;
        }

        if (loading) {
            return true;
        }

        loading = true;
        Study.service.loadNextCard(new StudyService.CardCallback() {
            @Override
            public void onCardLoaded(StudyCard loaded) {
                loading = false;
                card = loaded;
                showQuestion();
            }

            @Override
            public void onNoCardsDue() {
                loading = false;
                approvePendingAttack();
            }

            @Override
            public void onError(String message) {
                loading = false;
                showMessage(Messages.get(CombatStudy.class, "fallback", message));
                approvePendingAttack();
            }
        });

        return true;
    }

    public static void reset() {
        pendingHero = null;
        pendingTarget = null;
        card = null;
        loading = false;
        retryActive = false;
        result = null;
        attemptStartedAt = 0L;
    }

    private static void showQuestion() {
        if (pendingHero == null || pendingTarget == null || card == null) {
            cancelPendingAttack();
            return;
        }

        result = null;
        attemptStartedAt = Game.realTime;

        ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
                Messages.get(CombatStudy.class, "title"),
                card.question,
                "",
                512,
                false,
                Messages.get(CombatStudy.class, "check"),
                Messages.get(CombatStudy.class, "cancel"),
                true
        ) {
            @Override
            public void onSelect(boolean positive, String text) {
                if (!positive) {
                    cancelPendingAttack();
                    return;
                }
                if (pendingHero == null || card == null) return;
                revealAnswer(text == null ? "" : text);
            }

            @Override
            public void onBackPressed() {
                cancelPendingAttack();
                hide();
            }
        });
    }

    private static void revealAnswer(String typed) {
        String trimmed = typed.trim();
        String matched = TypedAnswerMatcher.matchedAlternative(trimmed, card.answer);

        if (trimmed.isEmpty() && card.reps == 0 && !retryActive) {
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
            body = Messages.get(CombatStudy.class, "result_revealed", card.answer);
        } else {
            title = Messages.get(CombatStudy.class, "incorrect");
            titleColor = ItemSlot.DEGRADED;
            body = Messages.get(CombatStudy.class, "result_incorrect", shownTyped, card.answer);
        }

        int count = Math.max(1, Math.min(4, card.buttonCount));
        String[] options = new String[count];

        for (int i = 0; i < count; i++) {
            String label = Messages.get(CombatStudy.class, EASE_KEYS[i]);
            String interval = i < card.nextReviewTimes.length ? card.nextReviewTimes[i] : "";
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
                chooseEase(index + 1);
            }

            @Override
            protected void onCancelled() {
                cancelPendingAttack();
            }
        });
    }

    private static void chooseEase(int pressedEase) {
        if (card == null || result == null || pendingHero == null) {
            cancelPendingAttack();
            return;
        }

        if (result == Result.WRONG && pressedEase == 1) {
            retryActive = true;
            result = null;
            failPendingAttack(true);
            return;
        }

        final int effectiveEase =
                result == Result.CORRECT && retryActive ? 1 : pressedEase;
        final boolean shouldAttack = result == Result.CORRECT;
        final StudyCard resolvedCard = card;
        final Hero hero = pendingHero;
        final Char target = pendingTarget;
        final long timeTaken = Math.max(0L, Game.realTime - attemptStartedAt);

        Study.service.answer(resolvedCard, effectiveEase, timeTaken, new StudyService.AnswerCallback() {
            @Override
            public void onAnswered() {
                card = null;
                retryActive = false;
                result = null;
                pendingHero = null;
                pendingTarget = null;

                if (shouldAttack) {
                    hero.studyAttackApproved(target);
                } else {
                    hero.studyAttackFailed();
                }
            }

            @Override
            public void onError(String message) {
                showMessage(Messages.get(CombatStudy.class, "save_error", message));
                // Keep the card unresolved so another attack can retry it.
                result = null;
                pendingHero = null;
                pendingTarget = null;
                hero.studyAttackCancelled();
            }
        });
    }

    private static void approvePendingAttack() {
        Hero hero = pendingHero;
        Char target = pendingTarget;

        pendingHero = null;
        pendingTarget = null;
        card = null;
        retryActive = false;
        result = null;

        if (hero != null) {
            hero.studyAttackApproved(target);
        }
    }

    private static void failPendingAttack(boolean keepCard) {
        Hero hero = pendingHero;

        pendingHero = null;
        pendingTarget = null;
        result = null;

        if (!keepCard) {
            card = null;
            retryActive = false;
        }

        if (hero != null) {
            hero.studyAttackFailed();
        }
    }

    private static void cancelPendingAttack() {
        Hero hero = pendingHero;
        pendingHero = null;
        pendingTarget = null;
        result = null;

        if (hero != null) {
            hero.studyAttackCancelled();
        }
    }

    private static void showMessage(String message) {
        if (ShatteredPixelDungeon.scene() != null) {
            ShatteredPixelDungeon.scene().addToFront(new WndMessage(message));
        }
    }
}
