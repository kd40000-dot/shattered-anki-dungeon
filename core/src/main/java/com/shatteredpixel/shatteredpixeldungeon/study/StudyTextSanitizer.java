/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

import java.util.regex.Pattern;

/** Normalizes Anki/provider text into characters SPD's bitmap UI can render. */
public final class StudyTextSanitizer {

    private static final Pattern TYPE_MARKER_SQUARE =
            Pattern.compile("(?i)\\[\\[\\s*type\\s*:[^\\]]+\\]\\]");
    private static final Pattern TYPE_MARKER_CURLY =
            Pattern.compile("(?i)\\{\\{\\s*type\\s*:[^}]+\\}\\}");

    private StudyTextSanitizer() {
    }

    /**
     * Anki's rendered question can contain markers such as [[type:Back]].
     * SAD supplies its own text box, so these placeholders are UI metadata,
     * not question content.
     */
    public static String question(String value) {
        String result = safe(value);
        result = TYPE_MARKER_SQUARE.matcher(result).replaceAll("");
        result = TYPE_MARKER_CURLY.matcher(result).replaceAll("");
        result = stripUnsupportedFormatting(result);
        return trimVisualWhitespace(result);
    }

    /**
     * Review interval strings may contain Unicode bidi isolate/format controls
     * inserted by Android/Anki formatting. SPD's bitmap font renders those as
     * replacement diamonds even though they are meant to be invisible.
     */
    public static String interval(String value) {
        String result = stripUnsupportedFormatting(safe(value));
        result = result.replace(' ', ' ');
        result = result.replace(' ', ' ');
        return result.trim();
    }

    public static String[] intervals(String[] values) {
        if (values == null) return new String[0];
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = interval(values[i]);
        }
        return result;
    }

    private static String stripUnsupportedFormatting(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            int type = Character.getType(cp);

            // FORMAT covers bidi isolates/marks (e.g. U+2068/U+2069).
            // U+FFFD is also stripped because some providers have already
            // replaced those invisible controls before SAD receives them.
            if (type != Character.FORMAT && cp != 0xFFFD) {
                out.appendCodePoint(cp);
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    private static String trimVisualWhitespace(String value) {
        String[] lines = value.split("\\R", -1);
        int first = 0;
        int last = lines.length - 1;

        while (first <= last && lines[first].trim().isEmpty()) first++;
        while (last >= first && lines[last].trim().isEmpty()) last--;

        if (first > last) return "";

        StringBuilder out = new StringBuilder();
        for (int i = first; i <= last; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lines[i].trim());
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
