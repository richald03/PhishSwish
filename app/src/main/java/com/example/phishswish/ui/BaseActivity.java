package com.example.phishswish.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.phishswish.R;

public abstract class BaseActivity extends AppCompatActivity {

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean online = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View noInternetBanner;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        setupNetworkCallback();
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        attachNoInternetBanner();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (connectivityManager != null && networkCallback != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
            updateOnlineState(checkInitialConnectivity());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) { }
        }
    }

    public boolean isOnline() {
        return online;
    }

    public void requireInternetOrShowMessage(@Nullable Runnable onOnlineAction) {
        if (online) {
            if (onOnlineAction != null) onOnlineAction.run();
        } else {
            showNoInternetMessage();
        }
    }

    protected void onOnlineStateChanged(boolean isOnline) {
        // Activities can override to react to connectivity changes (e.g., tint chips, disable buttons)
    }

    private void setupNetworkCallback() {
        if (connectivityManager == null) return;
        if (networkCallback != null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                updateOnlineState(true);
            }

            @Override
            public void onLost(Network network) {
                updateOnlineState(false);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                updateOnlineState(hasInternet);
            }
        };
    }

    private boolean checkInitialConnectivity() {
        if (connectivityManager == null) return true;
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateOnlineState(final boolean nowOnline) {
        mainHandler.post(() -> {
            online = nowOnline;
            if (noInternetBanner != null) {
                noInternetBanner.setVisibility(online ? View.GONE : View.VISIBLE);
            }
            onOnlineStateChanged(online);
        });
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
        banner.setVisibility(View.GONE);

        ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;

        container.addView(banner, lp);
        noInternetBanner = banner;
    }

    private void showNoInternetMessage() {
        if (noInternetBanner != null) {
            noInternetBanner.setVisibility(View.VISIBLE);
            noInternetBanner.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start();
        }
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
}

