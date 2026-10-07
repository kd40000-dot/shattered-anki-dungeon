/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.android.study;

import android.app.Activity;
import android.os.Build;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyCard;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyDiagnostics;
import com.shatteredpixel.shatteredpixeldungeon.study.StudyService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android implementation of the core study service.
 *
 * All ContentProvider I/O is serialized on one worker thread. Permission
 * requests are explicitly completed through onRequestPermissionsResult so no
 * gameplay action survives across the Android permission dialog.
 */
public final class AndroidAnkiStudyService implements StudyService {

    public static final int REQUEST_ANKI_DATABASE_PERMISSION = 0x5341;

    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final Object permissionLock = new Object();
    private final List<AccessCallback> pendingAccessCallbacks = new ArrayList<>();
    private boolean permissionRequestInFlight;
    private volatile boolean destroyed;

    public AndroidAnkiStudyService(Activity activity) {
        this.activity = activity;
    }

    private AndroidAnkiBridge connect() {
        try {
            return AndroidAnkiBridge.connect(activity);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean backendAvailable() {
        return connect() != null;
    }

    @Override
    public String backendName() {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            return "Unavailable";
        }
        return bridge.backend().kind == AndroidAnkiBridge.BackendKind.RETRY
                ? "AnkiDroid Retry"
                : "AnkiDroid";
    }

    @Override
    public boolean hasAccess() {
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            return false;
        }
        try {
            return bridge.hasPermission();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean requestAccess(AccessCallback callback) {
        if (destroyed) {
            postAccess(callback, false, "The game activity is shutting down.");
            return false;
        }

        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            postAccess(callback, false, "No compatible AnkiDroid installation was found.");
            return false;
        }

        if (bridge.hasPermission()) {
            postAccess(callback, true, "AnkiDroid access is ready.");
            return true;
        }

        synchronized (permissionLock) {
            if (callback != null) {
                pendingAccessCallbacks.add(callback);
            }
            if (permissionRequestInFlight) {
                return false;
            }
            permissionRequestInFlight = true;
        }

        activity.runOnUiThread(() -> {
            if (activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
                finishAccessRequest(false, "The game activity is not available.");
                return;
            }

            AndroidAnkiBridge current = connect();
            if (current == null) {
                finishAccessRequest(false, "No compatible AnkiDroid installation was found.");
                return;
            }
            if (current.hasPermission()) {
                finishAccessRequest(true, "AnkiDroid access is ready.");
                return;
            }

            try {
                current.requestPermission(activity, REQUEST_ANKI_DATABASE_PERMISSION);
            } catch (RuntimeException e) {
                finishAccessRequest(false, errorMessage(e));
            }
        });

        return false;
    }

    /**
     * Called by AndroidLauncher. Returns true when this result belonged to the
     * Anki permission request.
     */
    public boolean onRequestPermissionsResult(int requestCode) {
        if (requestCode != REQUEST_ANKI_DATABASE_PERMISSION) {
            return false;
        }

        boolean granted = hasAccess();
        finishAccessRequest(
                granted,
                granted
                        ? "AnkiDroid access granted. Try the study action again."
                        : "AnkiDroid access was not granted."
        );
        return true;
    }

    /** Covers OEMs that resume the Activity before dispatching the callback. */
    public void onHostResume() {
        boolean shouldReconcile;
        synchronized (permissionLock) {
            shouldReconcile = permissionRequestInFlight;
        }
        if (shouldReconcile && hasAccess()) {
            finishAccessRequest(true, "AnkiDroid access granted. Try the study action again.");
        }
    }

    public void onHostDestroy() {
        destroyed = true;
        finishAccessRequest(false, "The game closed before AnkiDroid access finished.");
        worker.shutdownNow();
    }

    private void finishAccessRequest(boolean granted, String message) {
        List<AccessCallback> callbacks;
        synchronized (permissionLock) {
            if (!permissionRequestInFlight && pendingAccessCallbacks.isEmpty()) {
                return;
            }
            permissionRequestInFlight = false;
            callbacks = new ArrayList<>(pendingAccessCallbacks);
            pendingAccessCallbacks.clear();
        }

        for (AccessCallback callback : callbacks) {
            postAccess(callback, granted, message);
        }
    }

    private static void postAccess(AccessCallback callback, boolean granted, String message) {
        if (callback != null) {
            post(() -> callback.onAccessResult(granted, message));
        }
    }

    @Override
    public void loadNextCard(CardCallback callback) {
        StudyDiagnostics.mark("provider loadNextCard requested backend=" + backendName());
        AndroidAnkiBridge bridge = connect();
        if (bridge == null) {
            post(() -> callback.onError("No compatible AnkiDroid installation was found."));
            return;
        }
        if (!bridge.hasPermission()) {
            post(() -> callback.onError("AnkiDroid database access has not been granted."));
            return;
        }

        if (destroyed) {
            post(() -> callback.onError("The game activity is shutting down."));
            return;
        }

        try {
            worker.execute(() -> {
                try {
                    AndroidAnkiBridge.ReviewCard card = bridge.loadNextCard();
                StudyDiagnostics.mark("provider loadNextCard worker result="
                        + (card == null ? "null" : "note=" + card.noteId + " ord=" + card.ord));
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
        } catch (RuntimeException e) {
            post(() -> callback.onError(errorMessage(e)));
        }
    }

    @Override
    public void answer(StudyCard card, int ease, long timeTakenMs, AnswerCallback callback) {
        StudyDiagnostics.mark("provider answer requested note="
                + (card == null ? "null" : card.noteId + "/" + card.ord)
                + " ease=" + ease + " ms=" + timeTakenMs);
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

        if (destroyed) {
            post(() -> callback.onError("The game activity is shutting down."));
            return;
        }

        try {
            worker.execute(() -> {
                try {
                    boolean accepted = bridge.answer(card.noteId, card.ord, ease, timeTakenMs);
                StudyDiagnostics.mark("provider answer worker accepted=" + accepted
                        + " note=" + card.noteId + "/" + card.ord);
                if (accepted) {
                    post(callback::onAnswered);
                } else {
                    post(() -> callback.onError("AnkiDroid did not accept the review result."));
                }
                } catch (Exception e) {
                    post(() -> callback.onError(errorMessage(e)));
                }
            });
        } catch (RuntimeException e) {
            post(() -> callback.onError(errorMessage(e)));
        }
    }

    private static void post(Runnable runnable) {
        Application app = Gdx.app;
        if (app == null) {
            return;
        }
        try {
            app.postRunnable(runnable);
        } catch (RuntimeException ignored) {
            // The Activity/render loop is shutting down; stale callbacks are dropped.
        }
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }
}
