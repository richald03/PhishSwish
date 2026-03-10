package com.example.phishswish.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.example.phishswish.R;
import com.example.phishswish.utils.LanguageManager;
import com.example.phishswish.utils.NetworkMonitor;

public abstract class BaseActivity extends AppCompatActivity {

    private boolean online = true;
    private String currentLang;
    private View noInternetBanner;
    private NetworkMonitor.Listener networkListener;

    // Navigation loading state
    private ProgressBar navLoadingProgress;
    private boolean isNavLoading = false;
    private final Handler navHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.applyLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.example.phishswish.utils.ThemeManager.applyTheme(this);
        currentLang = LanguageManager.getLanguage(this);
        setupNetworkMonitoring();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentLang != null && !currentLang.equals(LanguageManager.getLanguage(this))) {
            recreate();
        }
        // Always hide navigation loading when this activity resumes
        hideNavLoading();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        attachNoInternetBanner();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (networkListener != null) {
            NetworkMonitor.getInstance().addListener(networkListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkListener != null) {
            NetworkMonitor.getInstance().removeListener(networkListener);
        }
    }

    public boolean isOnline() {
        return online;
    }

    public void requireInternetOrShowMessage(@Nullable Runnable onOnlineAction) {
        if (online) {
            if (onOnlineAction != null) onOnlineAction.run();
        } else {
            Toast.makeText(this, getString(R.string.msg_no_internet_check), Toast.LENGTH_SHORT).show();
            if (noInternetBanner != null) {
                noInternetBanner.setVisibility(View.VISIBLE);
                noInternetBanner.setAlpha(0f);
                noInternetBanner.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    protected void onOnlineStateChanged(boolean isOnline) {
        // Activities can override
    }

    private void setupNetworkMonitoring() {
        networkListener = isOnline -> {
            online = isOnline;
            if (noInternetBanner != null) {
                noInternetBanner.setVisibility(online ? View.GONE : View.VISIBLE);
                if (!online) {
                    noInternetBanner.setAlpha(0f);
                    noInternetBanner.animate().alpha(1f).setDuration(200).start();
                }
            }
            onOnlineStateChanged(online);
        };
    }

    private void attachNoInternetBanner() {
        View root = findViewById(R.id.main);
        if (!(root instanceof ConstraintLayout)) return;
        ConstraintLayout container = (ConstraintLayout) root;

        if (noInternetBanner != null && noInternetBanner.getParent() == container) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View banner = inflater.inflate(R.layout.view_no_internet_banner, container, false);
        banner.setVisibility(online ? View.GONE : View.VISIBLE);

        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        container.addView(banner, 0, lp);
        noInternetBanner = banner;
    }

    protected void enableAvatarPreview(@Nullable View avatarView) {
        if (!(avatarView instanceof ImageView)) return;

        avatarView.setOnLongClickListener(v -> {
            ImageView source = (ImageView) v;
            if (source.getDrawable() == null) return true;

            ImageView preview = new ImageView(this);
            preview.setImageDrawable(source.getDrawable());
            preview.setAdjustViewBounds(true);
            preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            preview.setPadding(padding, padding, padding, padding);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(preview)
                    .create();
            dialog.show();

            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            return true;
        });
    }

    // ========================
    // Navigation Loading System
    // ========================

    /**
     * Navigate to another activity with the existing blue loading animation.
     * Prevents double taps and auto-hides the loader when returning.
     */
    protected void navigateWithLoading(Class<?> targetActivity) {
        navigateWithLoading(new Intent(this, targetActivity));
    }

    /**
     * Navigate using a pre-built Intent with the existing blue loading animation.
     */
    protected void navigateWithLoading(Intent intent) {
        if (isNavLoading) return; // Prevent double taps
        isNavLoading = true;
        showNavLoading();

        navHandler.postDelayed(() -> {
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Safety: hide loader if startActivity fails
                hideNavLoading();
            }
        }, 350);

        // Safety timeout: reset loading state if onResume never fires
        navHandler.postDelayed(this::hideNavLoading, 3000);
    }

    private void showNavLoading() {
        View root = findViewById(R.id.main);
        if (!(root instanceof ConstraintLayout)) return;
        ConstraintLayout container = (ConstraintLayout) root;

        // Reuse existing nav_progress if the layout already has one
        View existing = container.findViewById(R.id.nav_progress);
        if (existing instanceof ProgressBar) {
            navLoadingProgress = (ProgressBar) existing;
        } else if (navLoadingProgress == null || navLoadingProgress.getParent() != container) {
            // Create one programmatically, matching the existing style:
            // 48dp, centered, colorPrimary tint, elevation 10dp
            navLoadingProgress = new ProgressBar(this);
            int size = (int) (48 * getResources().getDisplayMetrics().density);
            ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(size, size);
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            navLoadingProgress.setLayoutParams(lp);
            navLoadingProgress.setIndeterminate(true);
            navLoadingProgress.setIndeterminateTintList(
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorPrimary)));
            navLoadingProgress.setElevation(10 * getResources().getDisplayMetrics().density);
            navLoadingProgress.setVisibility(View.GONE);
            container.addView(navLoadingProgress);
        }

        navLoadingProgress.setVisibility(View.VISIBLE);
    }

    protected void hideNavLoading() {
        isNavLoading = false;
        if (navLoadingProgress != null) {
            navLoadingProgress.setVisibility(View.GONE);
        }
        navHandler.removeCallbacksAndMessages(null);
    }
}
