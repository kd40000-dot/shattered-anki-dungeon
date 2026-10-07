/*
 * Shattered Anki Dungeon
 * Copyright (C) 2026
 *
 * GPL-3.0-or-later
 */

package com.shatteredpixel.shatteredpixeldungeon.android.study;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import com.shatteredpixel.shatteredpixeldungeon.study.StudyDiagnostics;
import com.watabou.noosa.Game;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persists uncaught Java crashes with recent study breadcrumbs, then surfaces
 * the report on the next launch so it can be copied without adb/logcat.
 */
public final class StudyCrashReporter {

    private static final String PREFS = "sad_study_crash_report";
    private static final String KEY_REPORT = "report";

    private static final AtomicBoolean installed = new AtomicBoolean();
    private static final AtomicBoolean shownThisProcess = new AtomicBoolean();

    private StudyCrashReporter() {
    }

    public static void install(Context context) {
        if (!installed.compareAndSet(false, true)) return;

        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String report = buildReport(appContext, thread, throwable);
                SharedPreferences prefs =
                        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_REPORT, report).commit();
            } catch (Throwable ignored) {
                // Never let the diagnostics path replace the original crash.
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    public static void clearPending(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_REPORT)
                .apply();
        shownThisProcess.set(false);
    }

    public static void showPending(Activity activity) {
        if (!shownThisProcess.compareAndSet(false, true)) return;

        SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String report = prefs.getString(KEY_REPORT, null);
        if (report == null || report.trim().isEmpty()) return;

        activity.getWindow().getDecorView().post(() -> {
            if (activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && activity.isDestroyed())) {
                shownThisProcess.set(false);
                return;
            }

            TextView text = new TextView(activity);
            int pad = (int)(16 * activity.getResources().getDisplayMetrics().density);
            text.setPadding(pad, pad, pad, pad);
            text.setText(report);
            text.setTextIsSelectable(true);
            text.setMovementMethod(new ScrollingMovementMethod());

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Shattered Anki Dungeon crash report")
                    .setMessage("A crash was captured. Copy this report and paste it into our ChatGPT project.")
                    .setView(text)
                    .setPositiveButton("Copy", (d, which) -> {
                        ClipboardManager clipboard = (ClipboardManager)
                                activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(
                                    ClipData.newPlainText("SAD crash report", report));
                        }
                    })
                    .setNegativeButton("Clear", (d, which) ->
                            prefs.edit().remove(KEY_REPORT).apply())
                    .setNeutralButton("Close", null)
                    .create();

            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        });
    }

    private static String buildReport(Context context, Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));

        String version = "unknown";
        try {
            version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        return "Shattered Anki Dungeon crash report\n"
                + "Version: " + version + "\n"
                + "Package: " + context.getPackageName() + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Thread: " + (thread == null ? "unknown" : thread.getName()) + "\n"
                + "Game version: " + Game.version + "\n\n"
                + "Recent study breadcrumbs:\n"
                + StudyDiagnostics.snapshot()
                + "\nException:\n"
                + sw;
    }
}
