package com.example.phishswish.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.example.phishswish.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class BottomNavHelper {

    private static boolean isNavigating = false;

    private BottomNavHelper() {
    }

    public static void setup(
            final Activity activity,
            final BottomNavigationView bottomNav,
            final ProgressBar navProgress,
            final int currentMenuId
    ) {
        if (bottomNav == null || activity == null) return;

        bottomNav.setSelectedItemId(currentMenuId);

        bottomNav.setOnItemSelectedListener(item ->
                handleItemSelected(activity, bottomNav, navProgress, currentMenuId, item));
    }

    public static void onResume(
            final Activity activity,
            final BottomNavigationView bottomNav,
            final ProgressBar navProgress,
            final int currentMenuId
    ) {
        if (bottomNav == null) return;

        isNavigating = false;
        bottomNav.setEnabled(true);
        if (navProgress != null) {
            navProgress.setVisibility(View.GONE);
        }

        bottomNav.setSelectedItemId(currentMenuId);
    }

    private static boolean handleItemSelected(
            final Activity activity,
            final BottomNavigationView bottomNav,
            final ProgressBar navProgress,
            final int currentMenuId,
            final MenuItem item
    ) {
        if (bottomNav == null || activity == null || item == null) {
            return false;
        }

        if (isNavigating) {
            return true;
        }

        final int itemId = item.getItemId();
        if (itemId == currentMenuId) {
            return true;
        }

        final Class<?> targetActivity = resolveTargetActivity(itemId);
        if (targetActivity == null) {
            return false;
        }

        isNavigating = true;
        bottomNav.setEnabled(false);
        if (navProgress != null) {
            navProgress.setVisibility(View.VISIBLE);
        }

        Intent intent = new Intent(activity, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        activity.startActivity(intent);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isNavigating) {
                isNavigating = false;
                if (bottomNav != null) {
                    bottomNav.setEnabled(true);
                }
                if (navProgress != null) {
                    navProgress.setVisibility(View.GONE);
                }
            }
        }, 1200);

        return true;
    }

    private static Class<?> resolveTargetActivity(int itemId) {
        if (itemId == R.id.nav_home) {
            return MainActivity.class;
        } else if (itemId == R.id.nav_inbox) {
            return InboxScanActivity.class;
        } else if (itemId == R.id.nav_history) {
            return HistoryActivity.class;
        } else if (itemId == R.id.nav_settings) {
            return SettingsActivity.class;
        }
        return null;
    }
}

