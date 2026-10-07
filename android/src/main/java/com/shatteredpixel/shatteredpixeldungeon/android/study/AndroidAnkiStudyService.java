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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Html;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyCard;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android implementation of the core study service, backed by AnkiDroid. */
public final class AndroidAnkiStudyService implements StudyService {

    public static final int REQUEST_ANKI_DATABASE_PERMISSION = 0x5341;

    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public AndroidAnkiStudyService(Activity activity) {
        this.activity = activity;
    }

    private AndroidAnkiBridge connect() {
        return AndroidAnkiBridge.connect(activity);
    }

    @Override
    public boolean backendAvailable() {
        return AndroidAnkiBridge.discoverBackend(activity) != null;
    }

    @Override
    public String backendName() {
        AndroidAnkiBridge.BackendInfo backend = AndroidAnkiBridge.discoverBackend(activity);
        if (backend == null) {
            return "Unavailable";
        }
        return backend.kind == AndroidAnkiBridge.BackendKind.RETRY
                ? "AnkiDroid Retry"
                : "AnkiDroid";
    }

    @Override
    public boolean hasAccess() {
        AndroidAnkiBridge bridge = connect();
        return bridge != null && bridge.hasPermission();
    }

    @Override
    public boolean requestAccess() {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            return false;
        }
        if (bridge.hasPermission()) {
            return true;
        }

        // LibGDX gameplay runs on the GL/render thread. Android permission UI
        // must be launched on the Activity's UI thread.
        activity.runOnUiThread(() -> {
            AndroidAnkiBridge current = connect();
            if (current != null && !current.hasPermission()) {
                current.requestPermission(activity, REQUEST_ANKI_DATABASE_PERMISSION);
            }
        });
        return false;
    }

    @Override
    public void requestTypedAnswer(
            String title,
            String prompt,
            String positiveLabel,
            String negativeLabel,
            TextInputCallback callback
    ) {
        activity.runOnUiThread(() -> {
            final boolean[] resolved = {false};

            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setImeOptions(EditorInfo.IME_ACTION_DONE);
            input.setSelectAllOnFocus(false);

            CharSequence message;
            String htmlPrompt = prompt == null ? "" : prompt.replace("\n", "<br>");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                message = Html.fromHtml(htmlPrompt, Html.FROM_HTML_MODE_LEGACY);
            } else {
                //noinspection deprecation
                message = Html.fromHtml(htmlPrompt);
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(positiveLabel, null)
                    .setNegativeButton(negativeLabel, (d, which) -> {
                        if (!resolved[0]) {
                            resolved[0] = true;
                            post(callback::onCancelled);
                        }
                    })
                    .create();

            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnCancelListener(d -> {
                if (!resolved[0]) {
                    resolved[0] = true;
                    post(callback::onCancelled);
                }
            });

            dialog.setOnShowListener(d -> {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (resolved[0]) return;
                    resolved[0] = true;
                    String value = input.getText() == null ? "" : input.getText().toString();
                    dialog.dismiss();
                    post(() -> callback.onSubmitted(value));
                });

                input.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE && !resolved[0]) {
                        resolved[0] = true;
                        String value = input.getText() == null ? "" : input.getText().toString();
                        dialog.dismiss();
                        post(() -> callback.onSubmitted(value));
                        return true;
                    }
                    return false;
                });

                input.requestFocus();
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            });

            dialog.show();
        });
    }

    @Override
    public void loadNextCard(CardCallback callback) {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            post(() -> callback.onError("No compatible AnkiDroid installation was found."));
            return;
        }
        if (!bridge.hasPermission()) {
            post(() -> callback.onError("AnkiDroid database access has not been granted."));
            return;
        }

        worker.execute(() -> {
            try {
                AndroidAnkiBridge.ReviewCard card = bridge.loadNextCard();
                if (card == null) {
                    post(callback::onNoCardsDue);
                    return;
                }

                StudyCard studyCard = new StudyCard(
                        card.noteId,
                        card.ord,
                        card.reps,
                        card.buttonCount,
                        card.question,
                        card.answer,
                        card.nextReviewTimes,
                        card.mediaFiles
                );
                post(() -> callback.onCardLoaded(studyCard));
            } catch (Exception e) {
                post(() -> callback.onError(errorMessage(e)));
            }
        });
    }

    @Override
    public void answer(StudyCard card, int ease, long timeTakenMs, AnswerCallback callback) {
        if (card == null) {
            post(() -> callback.onError("No review card is active."));
            return;
        }

        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            post(() -> callback.onError("No compatible AnkiDroid installation was found."));
            return;
        }
        if (!bridge.hasPermission()) {
            post(() -> callback.onError("AnkiDroid database access has not been granted."));
            return;
        }

        worker.execute(() -> {
            try {
                if (bridge.answer(card.noteId, card.ord, ease, timeTakenMs)) {
                    post(callback::onAnswered);
                } else {
                    post(() -> callback.onError("AnkiDroid did not accept the review result."));
                }
            } catch (Exception e) {
                post(() -> callback.onError(errorMessage(e)));
            }
        });
    }

    private static void post(Runnable runnable) {
        if (Gdx.app != null) {
            Gdx.app.postRunnable(runnable);
        } else {
            runnable.run();
        }
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }
}
