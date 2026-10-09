/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.study;

/**
 * Serializes study interactions and rejects stale asynchronous callbacks.
 *
 * A token belongs to one logical study session. Any callback must prove that
 * its token is still current before it can mutate gameplay or UI.
 */
public final class StudySessionGuard {

    public enum Owner {
        COMBAT,
        CONJURE_FOOD,
        SPELL
    }

    private static long generation;
    private static Owner owner;

    private StudySessionGuard() {
    }

    public static synchronized long begin(Owner requestedOwner) {
        if (owner != null) {
            return 0L;
        }
        owner = requestedOwner;
        return ++generation;
    }

    public static synchronized boolean isCurrent(Owner expectedOwner, long token) {
        return token != 0L && owner == expectedOwner && generation == token;
    }

    public static synchronized void finish(Owner expectedOwner, long token) {
        if (isCurrent(expectedOwner, token)) {
            owner = null;
            generation++;
        }
    }

    public static synchronized void reset() {
        owner = null;
        generation++;
    }

    public static synchronized boolean busy() {
        return owner != null;
    }

    public static synchronized Owner owner() {
        return owner;
    }
}
