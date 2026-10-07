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

/** Matching rules shared with the existing typed-review integration. */
public final class TypedAnswerMatcher {

    private TypedAnswerMatcher() {
    }

    /**
     * @return the exact accepted alternative that matched, or null.
     *
     * Matching is case-insensitive, accent-sensitive and punctuation-sensitive.
     * Leading/trailing whitespace is ignored. A literal pipe separates accepted
     * alternatives.
     */
    public static String matchedAlternative(String typed, String accepted) {
        String candidate = typed == null ? "" : typed.trim();
        String answer = accepted == null ? "" : accepted;

        for (String alternative : answer.split("\\|", -1)) {
            String trimmed = alternative.trim();
            if (candidate.equalsIgnoreCase(trimmed)) {
                return trimmed;
            }
        }

        return null;
    }
}
