package com.example.phishswish.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.example.phishswish.R;
import com.example.phishswish.utils.LanguageManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.example.phishswish.utils.SafeClickListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {

    private ImageView ivAvatar;
    private TextView tvLanguageSubtitle;
    private ProgressBar navProgress;
    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        handleImageSelection(imageUri);
                    }
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openGallery();
                } else {
                    Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        ivAvatar = findViewById(R.id.ivAvatar);
        enableAvatarPreview(ivAvatar);
        navProgress = findViewById(R.id.nav_progress);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnUploadProfile).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                requireInternetOrShowMessage(() -> {
                    checkPermissionWithRationale();
                    completion.onComplete();
                });
            }
        });

        findViewById(R.id.llLogout).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                requireInternetOrShowMessage(() -> {
                    showLogoutConfirmation();
                    completion.onComplete();
                });
            }
        });

        // Add Change Password Logic
        findViewById(R.id.llChangePassword).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                requireInternetOrShowMessage(() -> {
                    showChangePasswordConfirmation();
                    completion.onComplete();
                });
            }
        });

        // FAQs navigation
        View llFaqs = findViewById(R.id.llFaqs);
        if (llFaqs != null) {
            llFaqs.setOnClickListener(v -> navigateWithLoading(FaqsActivity.class));
        }

        View llPrivacy = findViewById(R.id.llPrivacyPolicy);
        if (llPrivacy != null) {
            llPrivacy.setOnClickListener(v -> navigateWithLoading(PrivacyPolicyActivity.class));
        }

        View llAbout = findViewById(R.id.llAboutUs);
        if (llAbout != null) {
            llAbout.setOnClickListener(v -> navigateWithLoading(AboutUsActivity.class));
        }

        bottomNav = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setup(this, bottomNav, navProgress, R.id.nav_settings);

        View llLanguage = findViewById(R.id.llLanguage);
        tvLanguageSubtitle = findViewById(R.id.tvLanguageSubtitle);
        if (llLanguage != null) {
            llLanguage.setOnClickListener(v -> showLanguageDialog());
        }
        updateLanguageSubtitle();

        View llGlobalDeleting = findViewById(R.id.llGlobalDeleting);
        if (llGlobalDeleting != null) {
            llGlobalDeleting.setOnClickListener(new SafeClickListener() {
                @Override
                public void onSafeClick(View v, Completion completion) {
                    requireInternetOrShowMessage(() -> {
                        showFilterDeleteDialog();
                        completion.onComplete();
                    });
                }
            });
        }

        loadUserProfile();

        // Delete Account Logic
        View llDeleteAccount = findViewById(R.id.llDeleteAccount);
        if (llDeleteAccount != null) {
            llDeleteAccount.setOnClickListener(new SafeClickListener() {
                @Override
                public void onSafeClick(View v, Completion completion) {
                    requireInternetOrShowMessage(() -> {
                        showDeleteAccountConfirmation();
                        completion.onComplete();
                    });
                }
            });
        }
    }

    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_account_title))
                .setMessage(getString(R.string.dialog_delete_account_message))
                .setPositiveButton(getString(R.string.btn_delete_account), (dialog, which) -> showDeletePermanentConfirmation())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showDeletePermanentConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_delete_permanent_title))
                .setMessage(getString(R.string.dialog_delete_permanent_message))
                .setPositiveButton(getString(R.string.btn_delete_permanently), (dialog, which) -> performAccountDeletion())
                .setNegativeButton(getString(R.string.btn_back_custom), null)
                .show();
    }

    private void performAccountDeletion() {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        setNavLoading(true);
        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Delete Firestore Data
        // Deleting subcollections is tricky in client SDK, but we'll delete known paths
        db.collection("users").document(uid).collection("scans").get().addOnCompleteListener(task -> {
            WriteBatch batch = db.batch();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    batch.delete(doc.getReference());
                }
            }
            
            // Delete profile info
            batch.delete(db.collection("users").document(uid).collection("profile").document("info"));
            
            // Delete the user root document (optional but good practice)
            batch.delete(db.collection("users").document(uid));

            batch.commit().addOnCompleteListener(batchTask -> {
                // 2. Delete Auth Account
                user.delete().addOnCompleteListener(deleteTask -> {
                    setNavLoading(false);
                    if (deleteTask.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.msg_delete_account_success), Toast.LENGTH_SHORT).show();
                        
                        // 3. Clear local state and redirect
                        // Logout and clear task
                        FirebaseAuth.getInstance().signOut();
                        
                        // Clear SharedPreferences if any (assuming BaseActivity or similar might handle it if present, 
                        // but specifically redirecting to LoginActivity is requested)
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // Deletion failed (might require re-authentication)
                        showDeletionErrorDialog();
                    }
                });
            });
        });
    }

    private void showDeletionErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_deletion_failed))
                .setMessage(getString(R.string.msg_delete_account_failed))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show();
    }


    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.onResume(this, bottomNav, navProgress, R.id.nav_settings);
    }

    private void showChangePasswordConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_change_password_title))
                .setMessage(getString(R.string.dialog_change_password_message))
                .setPositiveButton(getString(R.string.btn_yes), (dialog, which) -> showVerifyCurrentPasswordDialog())
                .setNegativeButton(getString(R.string.btn_no), null)
                .show();
    }

    private void showVerifyCurrentPasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.hint_current_password));
        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(12, 12, 12, 12);

        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(48, 24, 48, 0);
        til.setLayoutParams(params);
        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_verify_identity_title))
                .setMessage(getString(R.string.dialog_verify_identity_message))
                .setView(container)
                .setPositiveButton(getString(R.string.btn_verify), null)
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = et.getText() != null ? et.getText().toString() : "";
                if (password.isEmpty()) {
                    til.setError(getString(R.string.error_password_empty));
                    return;
                }

                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
                user.reauthenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        dialog.dismiss();
                        showNewPasswordDialog();
                    } else {
                        til.setError(getString(R.string.error_password_incorrect));
                    }
                });
            });
        });
        dialog.show();
    }

    private void showNewPasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        TextInputLayout til = new TextInputLayout(this);
        til.setHint(getString(R.string.hint_new_password));
        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(12, 12, 12, 12);

        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        til.addView(et);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(48, 24, 48, 0);
        til.setLayoutParams(params);
        container.addView(til);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_update_password_title))
                .setMessage(getString(R.string.dialog_update_password_message))
                .setView(container)
                .setPositiveButton(getString(R.string.btn_update), null)
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newPass = et.getText() != null ? et.getText().toString() : "";
                if (newPass.length() < 6) {
                    til.setError(getString(R.string.error_password_too_short));
                    return;
                }

                user.updatePassword(newPass).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.msg_password_updated), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, getString(R.string.msg_password_update_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
        dialog.show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_logout_title))
                .setMessage(getString(R.string.dialog_logout_message))
                .setPositiveButton(getString(R.string.btn_yes), (dialog, which) -> performLogout())
                .setNegativeButton(getString(R.string.btn_no), null)
                .show();
    }

    private void performLogout() {
        if (!isOnline()) {
            requireInternetOrShowMessage(null);
            return;
        }
        setNavLoading(true);

        // Use a slight delay to show the blue progress bar
        new android.os.Handler().postDelayed(() -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 2000);
    }

    private void setNavLoading(boolean loading) {
        if (navProgress != null) navProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (bottomNav != null) bottomNav.setEnabled(!loading);
    }

    private void checkPermissionWithRationale() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_access_gallery_title))
                    .setMessage(getString(R.string.dialog_access_gallery_message))
                    .setPositiveButton(getString(R.string.btn_open), (dialog, which) -> openGallery())
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_permission_title))
                    .setMessage(getString(R.string.dialog_permission_message))
                    .setPositiveButton(getString(R.string.btn_allow), (dialog, which) -> requestPermissionLauncher.launch(permission))
                    .setNegativeButton(getString(R.string.btn_not_now), null)
                    .show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void handleImageSelection(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            ivAvatar.setImageBitmap(bitmap);
            String base64Image = encodeImage(bitmap);
            saveImageToFirestore(base64Image);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_image_load_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private String encodeImage(Bitmap bitmap) {
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private void saveImageToFirestore(String base64Image) {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("profileImage", base64Image);

        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .collection("profile")
                .document("info")
                .update(data)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, getString(R.string.msg_profile_updated), Toast.LENGTH_SHORT).show());
    }

    private void loadUserProfile() {
        if (!isOnline()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        TextView tvName = findViewById(R.id.tvProfileName);
        TextView tvEmail = findViewById(R.id.tvProfileEmail);

        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .collection("profile")
                .document("info")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String username = doc.getString("username");
                        String email = doc.getString("email");
                        String profileImage = doc.getString("profileImage");

                        if (username != null && !username.isEmpty()) tvName.setText(username);
                        if (email != null && !email.isEmpty()) tvEmail.setText(email);
                        if (profileImage != null && !profileImage.isEmpty()) {
                            byte[] bytes = Base64.decode(profileImage, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            ivAvatar.setImageBitmap(bitmap);
                        }
                    }
                });
    }

    private void updateLanguageSubtitle() {
        if (tvLanguageSubtitle == null) return;
        String lang = LanguageManager.getLanguage(this);
        tvLanguageSubtitle.setText("tl".equals(lang)
                ? getString(R.string.pref_language_subtitle_tl)
                : getString(R.string.pref_language_subtitle_en));
    }

    private void showLanguageDialog() {
        String currentLang = LanguageManager.getLanguage(this);
        final String[] languages = {
                getString(R.string.pref_language_subtitle_en),
                getString(R.string.pref_language_subtitle_tl)
        };
        final String[] codes = {"en", "tl"};
        int checkedItem = "tl".equals(currentLang) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_select_language))
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    String selectedCode = codes[which];
                    if (!selectedCode.equals(LanguageManager.getLanguage(this))) {
                        LanguageManager.setLocale(this, selectedCode);
                        dialog.dismiss();
                        recreate();
                    } else {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showFilterDeleteDialog() {
        final String[] options = {getString(R.string.filter_option_safe), getString(R.string.filter_option_spam), getString(R.string.filter_option_scam)};
        final boolean[] checked = {false, false, false};

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_filter_delete_title))
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton(getString(R.string.btn_delete), (dialog, which) -> {
                    boolean deleteSafe = checked[0];
                    boolean deleteSpam = checked[1];
                    boolean deleteScam = checked[2];
                    if (!deleteSpam && !deleteScam && !deleteSafe) {
                        Toast.makeText(this, getString(R.string.msg_select_at_least_one), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteFilteredHistory(deleteSpam, deleteScam, deleteSafe);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void deleteFilteredHistory(boolean deleteSpam, boolean deleteScam, boolean deleteSafe) {
        if (!isOnline()) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference scansRef = db.collection("users")
                .document(user.getUid())
                .collection("scans");

        java.util.List<String> labels = new java.util.ArrayList<>();
        if (deleteSpam) labels.add("SPAM");
        if (deleteScam) labels.add("SCAM");
        if (deleteSafe) labels.add("SAFE");

        scansRef.whereIn("label", labels)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        Toast.makeText(this, getString(R.string.msg_no_matching_history), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : query) {
                        batch.update(doc.getReference(), "hiddenInHistory", true);
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, getString(R.string.msg_filtered_history_cleared), Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, getString(R.string.msg_delete_failed), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, getString(R.string.msg_fetch_failed), Toast.LENGTH_SHORT).show());
    }
}
