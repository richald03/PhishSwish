package com.example.phishswish.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.phishswish.R;

public class NoInternetDialogFragment extends DialogFragment {

    public interface ActionListener {
        boolean hasInternet();
        void onStillNoInternetPressed();
    }

    private ActionListener listener;

    public NoInternetDialogFragment() {
        // Required empty public constructor
    }

    public NoInternetDialogFragment(ActionListener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.Theme_NoInternet_FullscreenDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.dialog_no_internet_fullscreen, container, false);

        View btnOk = v.findViewById(R.id.btnOkNoInternet);
        if (btnOk != null) {
            btnOk.setOnClickListener(view -> {
                if (listener != null && listener.hasInternet()) {
                    dismissAllowingStateLoss();
                } else if (listener != null) {
                    listener.onStillNoInternetPressed();
                }
            });
        }

        setCancelable(false);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );

            window.setBackgroundDrawableResource(android.R.color.transparent);

            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }
}
