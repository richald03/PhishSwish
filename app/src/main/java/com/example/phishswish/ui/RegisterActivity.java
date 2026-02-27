package com.example.phishswish.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleClient;

    private TextInputLayout tilUsername, tilEmail, tilPassword, tilConfirm;
    private TextInputEditText etUsername, etEmail, etPassword, etConfirm;
    private MaterialButton btnRegister, btnGoogle;
    private ProgressBar progressBar;
    private View formContent;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                setLoading(false);
                if (result.getResultCode() == RESULT_CANCELED) {
                    Toast.makeText(this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.getData() == null) return;
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    showGoogleSyncConfirmation(account);
                } catch (ApiException e) {
                    Toast.makeText(this, "Google authentication failed. Please try again.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tilUsername = findViewById(R.id.tilUsername);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);

        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etRegEmail);
        etPassword = findViewById(R.id.etRegPassword);
        etConfirm = findViewById(R.id.etRegConfirm);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogle = findViewById(R.id.btnGoogleRegister);
        progressBar = findViewById(R.id.progressBarRegister);
        formContent = findViewById(R.id.register_form_content);
        TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        setupTextWatchers();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        tvGoLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> validateAndRegister());
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
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilUsername.setError(null);
                tilEmail.setError(null);
                tilPassword.setError(null);
                tilConfirm.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        etUsername.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);
        etConfirm.addTextChangedListener(watcher);
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

    private void validateAndRegister() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

        if (TextUtils.isEmpty(username)) { tilUsername.setError("Please choose a username."); return; }
        if (username.length() < 3) { tilUsername.setError("Username must be at least 3 characters."); return; }
        if (username.length() > 20) { tilUsername.setError("Username cannot exceed 20 characters."); return; }
        if (username.contains(" ")) { tilUsername.setError("Username must not contain spaces."); return; }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required for password recovery.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email address.");
            return;
        }

        if (TextUtils.isEmpty(pass)) { tilPassword.setError("Create a strong password to secure your account."); return; }
        if (!isPasswordStrong(pass)) {
            tilPassword.setError("Password must be 8+ characters with uppercase, lowercase, number, and symbol.");
            return;
        }

        if (TextUtils.isEmpty(confirm)) { tilConfirm.setError("Please confirm your password."); return; }
        if (!pass.equals(confirm)) { tilConfirm.setError("Passwords do not match. Please re-enter."); return; }

        checkUsernameUniqueness(username, email, pass);
    }

    private void checkUsernameUniqueness(String username, String email, String pass) {
        setLoading(true);
        String usernameLower = username.toLowerCase();
        db.collection("usernames").document(usernameLower).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        setLoading(false);
                        tilUsername.setError("Username already taken. Choose another.");
                    } else {
                        performAuthRegistration(username, email, pass);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Registration failed. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    private void performAuthRegistration(String username, String email, String pass) {
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        saveProfileAndMapping(user.getUid(), username, email);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, friendlyAuthError(e), Toast.LENGTH_LONG).show();
                });
    }

    private void saveProfileAndMapping(String uid, String username, String email) {
        String usernameLower = username.toLowerCase();
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("email", email);
        profile.put("provider", "email");
        profile.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> mapping = new HashMap<>();
        mapping.put("uid", uid);
        mapping.put("email", email);
        mapping.put("usernameOriginal", username);
        mapping.put("createdAt", FieldValue.serverTimestamp());

        db.collection("usernames").document(usernameLower).set(mapping)
                .addOnSuccessListener(aVoid -> db.collection("users").document(uid).collection("profile").document("info").set(profile)
                        .addOnSuccessListener(v -> {
                            setLoading(false);
                            new AlertDialog.Builder(this)
                                    .setTitle("Account Created")
                                    .setMessage("Welcome to PhishSwish! Your account has been successfully created.")
                                    .setPositiveButton("Get Started", (dialog, which) -> goHome())
                                    .setCancelable(false)
                                    .show();
                        })
                        .addOnFailureListener(e -> {
                            setLoading(false);
                            Toast.makeText(this, "Account created, but profile setup failed. Please check your internet.", Toast.LENGTH_LONG).show();
                            goHome();
                        }))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Account created, but profile setup failed. Please check your internet.", Toast.LENGTH_LONG).show();
                    goHome();
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        checkIfFirstTimeGoogle(user);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Google authentication failed. Please try again.", Toast.LENGTH_LONG).show();
                });
    }

    private void checkIfFirstTimeGoogle(FirebaseUser user) {
        db.collection("users").document(user.getUid()).collection("profile").document("info").get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    if (!doc.exists()) {
                        recreateProfile(user, true);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Welcome Back")
                                .setMessage("Welcome back! You have signed in with Google.")
                                .setPositiveButton("Continue", (dialog, which) -> goHome())
                                .setCancelable(false)
                                .show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    goHome();
                });
    }

    private void recreateProfile(FirebaseUser user, boolean isFirstTime) {
        Map<String, Object> profile = new HashMap<>();
        String email = user.getEmail() != null ? user.getEmail() : "";
        String username = (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ? user.getDisplayName() : "User";
        
        profile.put("username", username);
        profile.put("email", email);
        profile.put("provider", "google");
        profile.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(user.getUid())
                .collection("profile")
                .document("info")
                .set(profile)
                .addOnCompleteListener(task -> {
                    if (isFirstTime) {
                        new AlertDialog.Builder(this)
                                .setTitle("Account Synced")
                                .setMessage("Your account has been successfully synced in via Google.")
                                .setPositiveButton("Get Started", (dialog, which) -> goHome())
                                .setCancelable(false)
                                .show();
                    } else {
                        goHome();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        btnRegister.setEnabled(!isLoading);
        btnGoogle.setEnabled(!isLoading);
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (formContent != null) formContent.setAlpha(isLoading ? 0.5f : 1.0f);
    }

    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = Pattern.compile("[A-Z]").matcher(password).find();
        boolean hasLower = Pattern.compile("[a-z]").matcher(password).find();
        boolean hasDigit = Pattern.compile("[0-9]").matcher(password).find();
        boolean hasSpecial = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]").matcher(password).find();
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private String friendlyAuthError(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "Registration failed. Check your internet connection.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password does not meet security requirements.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "This email is already registered. Please login instead.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many requests. Please try again later.";
        }
        return "Registration failed. Please try again.";
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
