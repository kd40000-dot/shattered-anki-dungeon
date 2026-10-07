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

/** Entry point used by core gameplay. Android injects the AnkiDroid implementation. */
public final class Study {

    public static StudyService service;

    private Study() {
    }

    public static boolean supported() {
        return service != null && service.backendAvailable();
    }
}
