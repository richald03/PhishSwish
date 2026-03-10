package com.example.phishswish.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced global network monitor that uses a single listener with the application context.
 */
public final class NetworkMonitor {

    private static NetworkMonitor instance;
    private final ConnectivityManager connectivityManager;
    private final Set<Listener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isOnline = true;

    public interface Listener {
        void onConnectivityChanged(boolean isOnline);
    }

    private NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        isOnline = checkInitialConnectivity();
        registerCallback();
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context.getApplicationContext());
        }
    }

    public static NetworkMonitor getInstance() {
        if (instance == null) {
            throw new IllegalStateException("NetworkMonitor must be initialized with init(Context) first.");
        }
        return instance;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onConnectivityChanged(isOnline);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void registerCallback() {
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateState(true);
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateState(checkInitialConnectivity()); // Final check to be sure
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                 boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                 updateState(hasInternet);
            }
        });
    }

    private boolean checkInitialConnectivity() {
        if (connectivityManager == null) return true;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            @SuppressWarnings("deprecation")
            android.net.NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();
            return activeInfo != null && activeInfo.isConnected();
        }
    }

    private void updateState(final boolean online) {
        if (online == isOnline) return;
        isOnline = online;
        handler.post(() -> {
            for (Listener listener : listeners) {
                listener.onConnectivityChanged(isOnline);
            }
        });
    }
}
