package com.example.phishswish.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.phishswish.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private MaterialButton btnSendReset;
    private ImageButton btnBack;
    private ProgressBar progressBar;
    private View formContent;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        auth = FirebaseAuth.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etForgotEmail);
        btnSendReset = findViewById(R.id.btnSendReset);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBarForgot);
        formContent = findViewById(R.id.form_content);

        setupTextWatcher();

        btnSendReset.setOnClickListener(v -> checkAndSendReset());
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupTextWatcher() {
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilEmail.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void checkAndSendReset() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Enter your registered email to reset your password.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address (example@gmail.com).");
            return;
        }

        setLoading(true);

        // Directly send reset email. Firebase handles verification internally.
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    Toast.makeText(this, "Password reset link sent. Please check your inbox and Spam/Junk folder.", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    String errorMsg = "Unable to send reset link. Please try again.";
                    if (e instanceof FirebaseNetworkException) {
                        errorMsg = "No internet connection. Check your network and try again.";
                    } else if (e instanceof FirebaseAuthInvalidUserException) {
                        errorMsg = "No account found with this email.";
                    } else if (e instanceof FirebaseTooManyRequestsException) {
                        errorMsg = "Too many requests. Please try again later.";
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean isLoading) {
        btnSendReset.setEnabled(!isLoading);
        btnBack.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (formContent != null) formContent.setAlpha(isLoading ? 0.5f : 1.0f);
    }
}
