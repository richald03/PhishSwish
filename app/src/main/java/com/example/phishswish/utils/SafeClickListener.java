package com.example.phishswish.utils;

import android.os.SystemClock;
import android.view.View;

/**
 * Debounced click listener that:
 * - Ignores rapid repeated taps within a given interval.
 * - Disables the clicked view while an operation is running.
 * - Requires the caller to signal when the operation has completed.
 */
public abstract class SafeClickListener implements View.OnClickListener {

    private static final long DEFAULT_INTERVAL_MS = 900L;

    private final long intervalMs;
    private long lastClickTime = 0L;
    private boolean isLocked = false;

    public interface Completion {
        void onComplete();
    }

    public SafeClickListener() {
        this(DEFAULT_INTERVAL_MS);
    }

    public SafeClickListener(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    @Override
    public final void onClick(final View v) {
        long now = SystemClock.elapsedRealtime();
        if (isLocked) {
            return;
        }
        if (now - lastClickTime < intervalMs) {
            return;
        }
        lastClickTime = now;
        isLocked = true;
        if (v != null) {
            v.setEnabled(false);
        }

        Completion completion = () -> {
            if (v != null) {
                v.post(() -> {
                    v.setEnabled(true);
                    isLocked = false;
                });
            } else {
                isLocked = false;
            }
        };

        try {
            onSafeClick(v, completion);
        } catch (Exception e) {
            completion.onComplete();
            throw e;
        }
    }

    /**
     * Called when a click is accepted.
     * Implementations MUST call completion.onComplete() once the operation (including async work) is finished.
     */
    public abstract void onSafeClick(View v, Completion completion);
}

