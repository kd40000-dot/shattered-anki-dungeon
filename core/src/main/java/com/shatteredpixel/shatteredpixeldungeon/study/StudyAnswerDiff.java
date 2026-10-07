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
 * Pure answer-comparison logic shared by the study UI.
 *
 * Matching remains case-insensitive but accent/punctuation-sensitive, matching
 * the typed-answer rules used elsewhere in the integration.
 */
public final class StudyAnswerDiff {

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

    private StudyAnswerDiff() {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static int editDistance(String leftRaw, String rightRaw) {
        String left = normalize(leftRaw);
        String right = normalize(rightRaw);

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

    public static String closestAlternative(String typed, String fullAnswer) {
        String[] alternatives = fullAnswer == null
                ? new String[]{""}
                : fullAnswer.split("\\|", -1);

        if (alternatives.length == 0) return "";

        String best = alternatives[0].trim();
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

    public static List<Piece> align(String typedRaw, String expectedRaw) {
        String typed = typedRaw == null ? "" : typedRaw.trim();
        String expected = expectedRaw == null ? "" : expectedRaw.trim();
        String typedCmp = typed.toLowerCase(Locale.ROOT);
        String expectedCmp = expected.toLowerCase(Locale.ROOT);

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

        List<Piece> reversed = new ArrayList<>();
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
}
