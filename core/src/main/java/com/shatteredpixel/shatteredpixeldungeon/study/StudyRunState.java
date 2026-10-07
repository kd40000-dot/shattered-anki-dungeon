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

import com.watabou.utils.Bundle;

/**
 * Per-run state for study-powered gameplay.
 *
 * This is deliberately platform-neutral. Android/AnkiDroid code reports a
 * successfully resolved review here; core gameplay decides what that progress
 * unlocks.
 */
public final class StudyRunState {

    private static final int[] CONJURE_FOOD_COSTS = {
            1, 2, 3, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10
    };

    private static final String CONJURE_FOOD_ENABLED = "conjure_food_enabled";
    private static final String COMBAT_ENABLED = "combat_enabled";
    private static final String CONJURED_RATIONS = "conjured_rations";
    private static final String REVIEWS_TOWARD_NEXT = "reviews_toward_next";

    private static boolean conjureFoodEnabled;
    private static boolean combatEnabled;
    private static int conjuredRations;
    private static int reviewsTowardNext;

    private StudyRunState() {
    }

    public static void reset(boolean conjureEnabled, boolean ankiCombatEnabled) {
        conjureFoodEnabled = conjureEnabled;
        combatEnabled = ankiCombatEnabled;
        conjuredRations = 0;
        reviewsTowardNext = 0;
    }

    public static boolean conjureFoodEnabled() {
        return conjureFoodEnabled;
    }

    public static void conjureFoodEnabled(boolean enabled) {
        conjureFoodEnabled = enabled;
    }

    public static boolean combatEnabled() {
        return combatEnabled;
    }

    public static void combatEnabled(boolean enabled) {
        combatEnabled = enabled;
    }

    public static int conjuredRations() {
        return conjuredRations;
    }

    public static int reviewsTowardNext() {
        return reviewsTowardNext;
    }

    public static int reviewsRequired() {
        return reviewsRequiredForRation(conjuredRations);
    }

    /**
     * Cost of the next ration after {@code alreadyConjured} successful
     * conjurations. The curve rises gently and is permanently capped at 10.
     */
    public static int reviewsRequiredForRation(int alreadyConjured) {
        int index = Math.max(0, Math.min(alreadyConjured, CONJURE_FOOD_COSTS.length - 1));
        return CONJURE_FOOD_COSTS[index];
    }

    /**
     * Records one card that was actually resolved and submitted to Anki.
     *
     * @return true exactly when this review completes the current conjuration.
     * The caller can use that edge to grant one conjured ration.
     */
    public static boolean recordResolvedReview() {
        if (!conjureFoodEnabled) {
            return false;
        }

        int required = reviewsRequired();
        reviewsTowardNext++;

        if (reviewsTowardNext >= required) {
            reviewsTowardNext = 0;
            conjuredRations++;
            return true;
        }

        return false;
    }

    public static void storeInBundle(Bundle bundle) {
        bundle.put(CONJURE_FOOD_ENABLED, conjureFoodEnabled);
        bundle.put(COMBAT_ENABLED, combatEnabled);
        bundle.put(CONJURED_RATIONS, conjuredRations);
        bundle.put(REVIEWS_TOWARD_NEXT, reviewsTowardNext);
    }

    public static void restoreFromBundle(
            Bundle bundle,
            boolean defaultConjureEnabled,
            boolean defaultCombatEnabled
    ) {
        if (bundle == null || bundle.isNull()) {
            reset(defaultConjureEnabled, defaultCombatEnabled);
            return;
        }

        conjureFoodEnabled = bundle.contains(CONJURE_FOOD_ENABLED)
                ? bundle.getBoolean(CONJURE_FOOD_ENABLED)
                : defaultConjureEnabled;

        combatEnabled = bundle.contains(COMBAT_ENABLED)
                ? bundle.getBoolean(COMBAT_ENABLED)
                : defaultCombatEnabled;

        conjuredRations = Math.max(0, bundle.getInt(CONJURED_RATIONS));
        reviewsTowardNext = Math.max(0, bundle.getInt(REVIEWS_TOWARD_NEXT));

        // Be defensive about old/corrupt saves while preserving valid partial progress.
        int required = reviewsRequired();
        if (reviewsTowardNext >= required) {
            reviewsTowardNext = Math.max(0, required - 1);
        }
    }
}
