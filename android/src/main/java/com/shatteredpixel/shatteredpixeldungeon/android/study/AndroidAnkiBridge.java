/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.shatteredpixel.shatteredpixeldungeon.android.study;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;

/**
 * Android-only adapter for AnkiDroid's public CardContentProvider.
 *
 * The gameplay layer must not depend on a specific AnkiDroid package. This bridge
 * discovers a compatible provider at runtime and prefers the user's custom Retry
 * build when both it and stock AnkiDroid are installed.
 */
public final class AndroidAnkiBridge {

    public enum BackendKind {
        RETRY,
        STOCK
    }

    public static final class BackendInfo {
        public final BackendKind kind;
        public final String packageName;
        public final String authority;
        public final String permission;
        public final int providerSpec;

        private BackendInfo(
                BackendKind kind,
                String packageName,
                String authority,
                String permission,
                int providerSpec
        ) {
            this.kind = kind;
            this.packageName = packageName;
            this.authority = authority;
            this.permission = permission;
            this.providerSpec = providerSpec;
        }

        public Uri scheduleUri() {
            return Uri.parse("content://" + authority + "/schedule");
        }

        public Uri cardUri(long noteId, int ord) {
            return Uri.parse("content://" + authority + "/notes/" + noteId + "/cards/" + ord);
        }

        public Uri noteUri(long noteId) {
            return Uri.parse("content://" + authority + "/notes/" + noteId);
        }

        public Uri modelUri(long modelId) {
            return Uri.parse("content://" + authority + "/models/" + modelId);
        }
    }

    public static final class ReviewCard {
        public final long noteId;
        public final int ord;
        public final int reps;
        public final int buttonCount;
        public final String question;
        public final String answer;
        public final String[] nextReviewTimes;
        public final String[] mediaFiles;

        private ReviewCard(
                long noteId,
                int ord,
                int reps,
                int buttonCount,
                String question,
                String answer,
                String[] nextReviewTimes,
                String[] mediaFiles
        ) {
            this.noteId = noteId;
            this.ord = ord;
            this.reps = reps;
            this.buttonCount = buttonCount;
            this.question = question == null ? "" : question;
            this.answer = answer == null ? "" : answer;
            this.nextReviewTimes = nextReviewTimes;
            this.mediaFiles = mediaFiles;
        }
    }

    private static final String PROVIDER_SPEC_META = "com.ichi2.anki.provider.spec";

    private static final Candidate[] CANDIDATES = new Candidate[]{
            new Candidate(
                    BackendKind.RETRY,
                    "com.ichi2.anki.retry",
                    "com.ichi2.anki.retry.flashcards",
                    "com.ichi2.anki.retry.permission.READ_WRITE_DATABASE"
            ),
            new Candidate(
                    BackendKind.STOCK,
                    "com.ichi2.anki",
                    "com.ichi2.anki.flashcards",
                    "com.ichi2.anki.permission.READ_WRITE_DATABASE"
            )
    };

    private static final class Candidate {
        final BackendKind kind;
        final String packageName;
        final String authority;
        final String permission;

        Candidate(BackendKind kind, String packageName, String authority, String permission) {
            this.kind = kind;
            this.packageName = packageName;
            this.authority = authority;
            this.permission = permission;
        }
    }

    private final Context context;
    private final ContentResolver resolver;
    private final BackendInfo backend;

    private AndroidAnkiBridge(Context context, BackendInfo backend) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.backend = backend;
    }

    /**
     * Returns the best installed compatible backend, preferring Retry over stock.
     * Returns null when neither provider can be resolved.
     */
    public static BackendInfo discoverBackend(Context context) {
        PackageManager pm = context.getPackageManager();

        for (Candidate candidate : CANDIDATES) {
            ProviderInfo info = pm.resolveContentProvider(candidate.authority, PackageManager.GET_META_DATA);
            if (info == null) {
                continue;
            }

            // Do not accidentally bind to an unrelated provider that copied an authority.
            if (info.packageName != null && !candidate.packageName.equals(info.packageName)) {
                continue;
            }

            int spec = 0;
            Bundle meta = info.metaData;
            if (meta != null) {
                spec = meta.getInt(PROVIDER_SPEC_META, 0);
            }

            return new BackendInfo(
                    candidate.kind,
                    candidate.packageName,
                    candidate.authority,
                    candidate.permission,
                    spec
            );
        }

        return null;
    }

    public static AndroidAnkiBridge connect(Context context) {
        BackendInfo backend = discoverBackend(context);
        return backend == null ? null : new AndroidAnkiBridge(context, backend);
    }

    public BackendInfo backend() {
        return backend;
    }

    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return context.checkSelfPermission(backend.permission) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission()) {
            activity.requestPermissions(new String[]{backend.permission}, requestCode);
        }
    }

    /**
     * Loads the next due card from AnkiDroid's public ReviewInfo contract.
     *
     * Call this off the render/UI thread.
     */
    public ReviewCard loadNextCard() {
        long noteId;
        int ord;
        int buttonCount;
        String nextReviewTimesJson;
        String mediaFilesJson;

        try (Cursor cursor = resolver.query(
                backend.scheduleUri(),
                new String[]{"note_id", "ord", "button_count", "next_review_times", "media_files"},
                "limit=1",
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            noteId = cursor.getLong(cursor.getColumnIndexOrThrow("note_id"));
            ord = cursor.getInt(cursor.getColumnIndexOrThrow("ord"));
            buttonCount = cursor.getInt(cursor.getColumnIndexOrThrow("button_count"));
            nextReviewTimesJson = cursor.getString(cursor.getColumnIndexOrThrow("next_review_times"));
            mediaFilesJson = cursor.getString(cursor.getColumnIndexOrThrow("media_files"));
        }

        String question;
        String fallbackAnswer;
        int reps;

        try (Cursor cursor = resolver.query(
                backend.cardUri(noteId, ord),
                new String[]{"question_simple", "answer_pure", "reps"},
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            question = cursor.getString(cursor.getColumnIndexOrThrow("question_simple"));
            fallbackAnswer = cursor.getString(cursor.getColumnIndexOrThrow("answer_pure"));
            reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps"));
        }

        String answer = loadBackField(noteId, fallbackAnswer);

        return new ReviewCard(
                noteId,
                ord,
                reps,
                buttonCount,
                question,
                answer,
                jsonArrayToStrings(nextReviewTimesJson),
                jsonArrayToStrings(mediaFilesJson)
        );
    }

    /**
     * Records one resolved review. Ease uses AnkiDroid values 1..4.
     */
    public boolean answer(ReviewCard card, int ease, long timeTakenMs) {
        if (card == null) {
            throw new IllegalArgumentException("card == null");
        }
        if (ease < 1 || ease > 4) {
            throw new IllegalArgumentException("ease must be 1..4");
        }

        ContentValues values = new ContentValues();
        values.put("note_id", card.noteId);
        values.put("ord", card.ord);
        values.put("answer_ease", ease);
        values.put("time_taken", Math.max(0L, timeTakenMs));

        return resolver.update(backend.scheduleUri(), values, null, null) > 0;
    }

    /**
     * Prefer a literal Back field when available. This preserves user-authored
     * multiple-answer syntax such as "answer one|answer two" consistently across
     * stock AnkiDroid and the Retry build.
     */
    private String loadBackField(long noteId, String fallback) {
        try {
            long modelId;
            String fieldData;

            try (Cursor note = resolver.query(
                    backend.noteUri(noteId),
                    new String[]{"mid", "flds"},
                    null,
                    null,
                    null
            )) {
                if (note == null || !note.moveToFirst()) {
                    return safe(fallback);
                }

                modelId = note.getLong(note.getColumnIndexOrThrow("mid"));
                fieldData = note.getString(note.getColumnIndexOrThrow("flds"));
            }

            String fieldNames;
            try (Cursor model = resolver.query(
                    backend.modelUri(modelId),
                    new String[]{"field_names"},
                    null,
                    null,
                    null
            )) {
                if (model == null || !model.moveToFirst()) {
                    return safe(fallback);
                }
                fieldNames = model.getString(model.getColumnIndexOrThrow("field_names"));
            }

            String[] names = safe(fieldNames).split("\\u001f", -1);
            String[] fields = safe(fieldData).split("\\u001f", -1);
            int count = Math.min(names.length, fields.length);

            for (int i = 0; i < count; i++) {
                if ("Back".equalsIgnoreCase(names[i].trim())) {
                    return fields[i];
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the provider-rendered answer.
        }

        return safe(fallback);
    }

    private static String[] jsonArrayToStrings(String json) {
        if (json == null || json.isEmpty()) {
            return new String[0];
        }

        try {
            JSONArray array = new JSONArray(json);
            String[] result = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                result[i] = array.optString(i, "");
            }
            return result;
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
