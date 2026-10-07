/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/** Small in-memory breadcrumb ring used only to diagnose study integration crashes. */
public final class StudyDiagnostics {

    private static final int MAX_ENTRIES = 60;
    private static final Deque<String> entries = new ArrayDeque<>();

    private StudyDiagnostics() {
    }

    public static synchronized void mark(String message) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.removeFirst();
        }
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        entries.addLast(time + " [" + Thread.currentThread().getName() + "] " + message);
    }

    public static synchronized String snapshot() {
        StringBuilder out = new StringBuilder();
        for (String entry : entries) {
            out.append(entry).append('\n');
        }
        return out.toString();
    }

    public static synchronized void clear() {
        entries.clear();
    }
}
