/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 * GPL-3.0-or-later
 */
package com.shatteredpixel.shatteredpixeldungeon.study;

import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.ClericSpell;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.spells.InventoryClericSpell;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HolyTome;
import com.shatteredpixel.shatteredpixeldungeon.ui.ItemSlot;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTextInput;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndStudyRating;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndMessage;
import com.watabou.noosa.Game;
import com.watabou.utils.Callback;

/** Serial review gate for Cleric spells; no cast or charge until a resolved correct review. */
public final class SpellStudy {
    private static final StudySessionGuard.Owner OWNER = StudySessionGuard.Owner.SPELL;
    private static final String[] KEYS = {"again", "hard", "good", "easy"};
    private static long token;
    private static StudyCard pendingCard;
    private static boolean forceAgain;

    private SpellStudy() {}

    public static void cast(ClericSpell spell, HolyTome tome, Hero hero) {
        if (spell == null || tome == null) return;
        // Targeted spells first choose a cell; their selection callback gates the effect.
        if (spell instanceof InventoryClericSpell || spell.usesTargeting() || spell.targetingFlags() != -1) {
            spell.onCast(tome, hero);
        } else {
            cast(() -> spell.onCast(tome, hero), hero);
        }
    }

    public static void cast(Runnable action, Hero hero) {
        if (action == null || hero == null) return;
        if (!StudyRunState.combatEnabled()) {
            action.run();
            return;
        }
        if (Study.service == null || !Study.service.backendAvailable() || !Study.service.hasAccess()) {
            message("AnkiDroid access is required to cast spells.");
            return;
        }
        if (StudySessionGuard.busy()) {
            message("Finish the current Anki review before casting a spell.");
            return;
        }
        long session = StudySessionGuard.begin(OWNER);
        if (session == 0) return;
        token = session;
        if (pendingCard != null) {
            StudyCard card = pendingCard;
            showQuestion(session, card, action, hero);
        } else {
            Study.service.loadNextCard(new StudyService.CardCallback() {
                @Override public void onCardLoaded(StudyCard card) {
                    if (!current(session)) return;
                    if (card == null) { finish(session); return; }
                    showQuestion(session, card, action, hero);
                }
                @Override public void onNoCardsDue() {
                    if (!current(session)) return;
                    finish(session);
                    if (hero.isAlive()) action.run();
                }
                @Override public void onError(String detail) {
                    if (!current(session)) return;
                    finish(session);
                    message("Unable to load Anki card: " + detail);
                }
            });
        }
    }

    private static boolean current(long session) {
        return token == session && StudySessionGuard.isCurrent(OWNER, session);
    }

    private static void showQuestion(long session, StudyCard card, Runnable action, Hero hero) {
        Game.runOnRenderThread(new Callback() {
            @Override public void call() {
                if (!current(session)) return;
                ShatteredPixelDungeon.scene().addToFront(new WndTextInput(
                        "Anki Spell", card.question, "", 512, false,
                        "Check", "Cancel Spell", true, true, null) {
                    private boolean revealing;
                    @Override public void onSelect(boolean positive, String text) {
                        if (!current(session)) return;
                        if (!positive) { finish(session); return; }
                        revealing = true;
                        reveal(session, card, text == null ? "" : text, action, hero);
                    }
                    @Override public void onBackPressed() {
                        finish(session);
                        hide();
                    }
                    @Override protected void onDismissed() {
                        if (!revealing) finish(session);
                    }
                });
            }
        });
    }

    private static void reveal(long session, StudyCard card, String typed, Runnable action, Hero hero) {
        if (!current(session)) return;
        boolean correct = TypedAnswerMatcher.matchedAlternative(typed, card.answer) != null;
        boolean firstBlank = typed.trim().isEmpty() && card.reps == 0 && !forceAgain;
        String[] options = new String[Math.max(1, Math.min(4, card.buttonCount))];
        for (int i = 0; i < options.length; i++) {
            int intervalIndex = correct && forceAgain ? 0 : i;
            String interval = intervalIndex < card.nextReviewTimes.length ? card.nextReviewTimes[intervalIndex] : "";
            options[i] = KEYS[i] + (interval == null || interval.isEmpty() ? "" : "\n" + interval);
        }
        Study.service.playAnswerAudio(card, typed);
        ShatteredPixelDungeon.scene().addToFront(new WndStudyRating(
                correct ? "Correct" : "Incorrect", correct ? ItemSlot.UPGRADED : ItemSlot.DEGRADED,
                card.question, typed, card.answer, firstBlank, "You:", "Answer:", options) {
            @Override protected void onSelect(int index) {
                if (!current(session)) return;
                if (!correct && index == 0) {
                    forceAgain = true;
                    pendingCard = card;
                    finish(session);
                    return;
                }
                int ease = correct && forceAgain ? 1 : index + 1;
                Study.service.answer(card, ease, 0L, new StudyService.AnswerCallback() {
                    @Override public void onAnswered() {
                        if (!current(session)) return;
                        StudyPreviousRating.record(ease);
                        pendingCard = null;
                        forceAgain = false;
                        finish(session);
                        if (correct && hero.isAlive()) action.run();
                    }
                    @Override public void onError(String detail) {
                        if (!current(session)) return;
                        finish(session);
                        message("Could not save spell review: " + detail);
                    }
                });
            }
            @Override protected void onCancelled() { finish(session); }
        });
    }

    private static void finish(long session) {
        if (!current(session)) return;
        StudySessionGuard.finish(OWNER, session);
        token = 0L;
    }

    public static void reset() {
        if (current(token)) StudySessionGuard.finish(OWNER, token);
        token = 0L;
        pendingCard = null;
        forceAgain = false;
    }

    private static void message(String value) {
        Game.runOnRenderThread(() -> {
            if (ShatteredPixelDungeon.scene() != null)
                ShatteredPixelDungeon.scene().addToFront(new WndMessage(value));
        });
    }
}
