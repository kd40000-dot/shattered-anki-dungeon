/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Character-level comparison used by the study answer UI.
 *
 * This intentionally mirrors the Andor's Trail review presentation:
 * the typed answer is aligned to the closest accepted alternative, then each
 * typed/expected character can be styled independently.
 */
public final class StudyAnswerDiff {

    private StudyAnswerDiff() {
    }

    public static final class Piece {
        public final Character typed;
        public final Character expected;
        public final boolean match;

        Piece(Character typed, Character expected, boolean match) {
            this.typed = typed;
            this.expected = expected;
            this.match = match;
        }
    }

    public static String closestAlternative(String typed, String fullAnswer) {
        String[] alternatives = fullAnswer == null
                ? new String[]{""}
                : fullAnswer.split("\\|", -1);

        String best = alternatives.length == 0 ? "" : alternatives[0].trim();
        int bestDistance = editDistance(typed, best);

        for (int i = 1; i < alternatives.length; i++) {
            String candidate = alternatives[i].trim();
            int distance = editDistance(typed, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }

    public static List<Piece> alignToClosestAlternative(String typed, String fullAnswer) {
        return align(typed, closestAlternative(typed, fullAnswer));
    }

    static int editDistance(String a, String b) {
        String left = normalize(a);
        String right = normalize(b);

        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= right.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[left.length()][right.length()];
    }

    private static List<Piece> align(String typedRaw, String expectedRaw) {
        String typed = typedRaw == null ? "" : typedRaw.trim();
        String expected = expectedRaw == null ? "" : expectedRaw.trim();
        String typedCmp = normalize(typed);
        String expectedCmp = normalize(expected);

        int n = typed.length();
        int m = expected.length();
        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = typedCmp.charAt(i - 1) == expectedCmp.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        ArrayList<Piece> reversed = new ArrayList<>();
        int i = n;
        int j = m;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                boolean match = typedCmp.charAt(i - 1) == expectedCmp.charAt(j - 1);
                int cost = match ? 0 : 1;
                if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                    reversed.add(new Piece(
                            typed.charAt(i - 1),
                            expected.charAt(j - 1),
                            match
                    ));
                    i--;
                    j--;
                    continue;
                }
            }

            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                reversed.add(new Piece(typed.charAt(i - 1), null, false));
                i--;
                continue;
            }

            reversed.add(new Piece(null, expected.charAt(j - 1), false));
            j--;
        }

        Collections.reverse(reversed);
        return reversed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
