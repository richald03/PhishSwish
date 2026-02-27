package com.example.phishswish.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.phishswish.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleClient;

    private TextInputLayout tilUsername, tilPassword;
    private TextInputEditText etUsername, etPassword;
    private MaterialButton btnLogin, btnGoogle;
    private ProgressBar progressBar;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                setLoading(false);
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.getData() == null) return;
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    showGoogleSyncConfirmation(account);
                } catch (ApiException e) {
                    if (e.getStatusCode() == CommonStatusCodes.CANCELED) {
                        Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Google authentication failed. Please try again.", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            goHome();
            return;
        }

        setContentView(R.layout.activity_login);

        tilUsername = (TextInputLayout) findViewById(R.id.etLoginUsername).getParent().getParent();
        tilPassword = (TextInputLayout) findViewById(R.id.etLoginPassword).getParent().getParent();
        etUsername = findViewById(R.id.etLoginUsername);
        etPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogle = findViewById(R.id.btnGoogleLogin);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);
        TextView tvForgot = findViewById(R.id.tvForgot);
        progressBar = findViewById(R.id.progressBar);

        setupTextWatchers();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> validateAndLogin());
        btnGoogle.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Google Sign-In")
                    .setMessage("PhishSwish will use your Google account for authentication and syncing scan history.")
                    .setPositiveButton("Continue", (dialog, which) -> {
                        setLoading(true);
                        googleLauncher.launch(googleClient.getSignInIntent());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
        tvForgot.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilUsername.setError(null);
                tilPassword.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        etUsername.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
    }

    private void showGoogleSyncConfirmation(GoogleSignInAccount account) {
        new AlertDialog.Builder(this)
                .setTitle("Sync Google account?")
                .setMessage("Use " + account.getEmail() + " to sign in and sync your scan history with PhishSwish?")
                .setPositiveButton("Continue", (dialog, which) -> {
                    setLoading(true);
                    firebaseAuthWithGoogle(account.getIdToken());
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    googleClient.signOut();
                    setLoading(false);
                })
                .setCancelable(false)
                .show();
    }

    private void validateAndLogin() {
        String input = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(input)) {
            tilUsername.setError("Please enter your username.");
            return;
        }

        if (input.contains("@")) {
            tilUsername.setError("Please enter your username only. For email login, use Google Sign-In.");
            return;
        }

        if (TextUtils.isEmpty(pass)) {
            tilPassword.setError("Enter your password.");
            return;
        }

        performLogin(input, pass);
    }

    private void performLogin(String input, String password) {
        setLoading(true);

        db.collection("usernames").document(input.toLowerCase()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String resolvedEmail = doc.getString("email");
                        if (resolvedEmail != null) {
                            auth.signInWithEmailAndPassword(resolvedEmail, password)
                                    .addOnSuccessListener(authResult -> showLoginSuccessDialog(authResult.getUser()))
                                    .addOnFailureListener(this::handleAuthError);
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "Error resolving username.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Username not found. Please register.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, friendlyAuthError(e), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoginSuccessDialog(FirebaseUser user) {
        setLoading(false);
        new AlertDialog.Builder(this)
                .setTitle("Login Successful")
                .setMessage("Welcome back! You have signed in successfully.")
                .setPositiveButton("Continue", (dialog, which) -> checkProfileAndHeal(user))
                .setCancelable(false)
                .show();
    }

    private void showGoogleAuthSuccessDialog(FirebaseUser user) {
        setLoading(false);
        db.collection("users").document(user.getUid()).collection("profile").document("info").get()
                .addOnSuccessListener(doc -> {
                    String title = doc.exists() ? "Welcome Back" : "Account Synced";
                    String message = doc.exists() ? "Welcome back! You have signed in with Google." : "Google account successfully synced in.";
                    
                    new AlertDialog.Builder(this)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton("Continue", (dialog, which) -> checkProfileAndHeal(user))
                            .setCancelable(false)
                            .show();
                })
                .addOnFailureListener(e -> showLoginSuccessDialog(user));
    }

    private void firebaseAuthWithGoogle(String idToken) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> showGoogleAuthSuccessDialog(authResult.getUser()))
                .addOnFailureListener(this::handleAuthError);
    }

    private void checkProfileAndHeal(FirebaseUser user) {
        if (user == null) {
            goHome();
            return;
        }

        db.collection("users").document(user.getUid()).collection("profile").document("info").get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        recreateProfile(user);
                    } else {
                        startLoadingEffect();
                    }
                })
                .addOnFailureListener(e -> startLoadingEffect());
    }

    private void recreateProfile(FirebaseUser user) {
        Map<String, Object> profile = new HashMap<>();
        String email = user.getEmail() != null ? user.getEmail() : "";
        String username = (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ? user.getDisplayName() : "User";
        
        profile.put("username", username);
        profile.put("email", email);
        profile.put("provider", email.endsWith("@phishswish.local") ? "email" : "google");
        profile.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(user.getUid())
                .collection("profile")
                .document("info")
                .set(profile)
                .addOnCompleteListener(task -> startLoadingEffect());
    }

    private void handleAuthError(Exception e) {
        setLoading(false);
        if (e instanceof FirebaseNetworkException) {
            Toast.makeText(this, "No internet connection. Check your network and try again.", Toast.LENGTH_LONG).show();
        } else if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
            Toast.makeText(this, "Incorrect login details. Please try again.", Toast.LENGTH_LONG).show();
        } else if (e instanceof FirebaseTooManyRequestsException) {
            Toast.makeText(this, "Too many attempts. Try again later.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Authentication failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private String friendlyAuthError(Exception e) {
        if (e instanceof FirebaseNetworkException) return "No internet connection.";
        return "Something went wrong.";
    }

    private void startLoadingEffect() {
        progressBar.setVisibility(View.VISIBLE);
        findViewById(R.id.scrollLogin).setVisibility(View.GONE);
        new Handler().postDelayed(this::goHome, 3000);
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnGoogle.setEnabled(!isLoading);
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
