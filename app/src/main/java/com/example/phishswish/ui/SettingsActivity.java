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
                    Toast.makeText(this, "Permission denied to access gallery", Toast.LENGTH_SHORT).show();
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
            llFaqs.setOnClickListener(v -> {
                Intent i = new Intent(this, FaqsActivity.class);
                startActivity(i);
            });
        }

        View llPrivacy = findViewById(R.id.llPrivacyPolicy);
        if (llPrivacy != null) {
            llPrivacy.setOnClickListener(v -> {
                Intent i = new Intent(this, PrivacyPolicyActivity.class);
                startActivity(i);
            });
        }

        View llAbout = findViewById(R.id.llAboutUs);
        if (llAbout != null) {
            llAbout.setOnClickListener(v -> {
                Intent i = new Intent(this, AboutUsActivity.class);
                startActivity(i);
            });
        }

        bottomNav = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setup(this, bottomNav, navProgress, R.id.nav_settings);

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.onResume(this, bottomNav, navProgress, R.id.nav_settings);
    }

    private void showChangePasswordConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage("Are you sure you want to change your password?")
                .setPositiveButton("Yes", (dialog, which) -> showVerifyCurrentPasswordDialog())
                .setNegativeButton("No", null)
                .show();
    }

    private void showVerifyCurrentPasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Current Password");
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
                .setTitle("Verify Identity")
                .setMessage("Please enter your current password to proceed.")
                .setView(container)
                .setPositiveButton("Verify", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = et.getText() != null ? et.getText().toString() : "";
                if (password.isEmpty()) {
                    til.setError("Password cannot be empty");
                    return;
                }

                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
                user.reauthenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        dialog.dismiss();
                        showNewPasswordDialog();
                    } else {
                        til.setError("Incorrect current password");
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
        til.setHint("New Password");
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
                .setTitle("Update Password")
                .setMessage("Enter your new password below.")
                .setView(container)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newPass = et.getText() != null ? et.getText().toString() : "";
                if (newPass.length() < 6) {
                    til.setError("Password must be at least 6 characters");
                    return;
                }

                user.updatePassword(newPass).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Failed to update password.", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
        dialog.show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        if (!isOnline()) {
            requireInternetOrShowMessage(null);
            return;
        }
        setNavLoading(true);
        findViewById(R.id.clHeader).setVisibility(View.GONE);
        findViewById(R.id.bottom_navigation).setVisibility(View.GONE);

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
                    .setTitle("Access Gallery")
                    .setMessage("Do you want to open your gallery to select a profile picture?")
                    .setPositiveButton("Open", (dialog, which) -> openGallery())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("PhishSwish needs access to your gallery to let you upload a profile picture. Would you like to grant permission?")
                    .setPositiveButton("Allow", (dialog, which) -> requestPermissionLauncher.launch(permission))
                    .setNegativeButton("Not Now", null)
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
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
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
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show());
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

    private void showFilterDeleteDialog() {
        final String[] options = {"Spam messages", "Scam messages"};
        final boolean[] checked = {false, false};

        new AlertDialog.Builder(this)
                .setTitle("Filter Delete History")
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleteSpam = checked[0];
                    boolean deleteScam = checked[1];
                    if (!deleteSpam && !deleteScam) {
                        Toast.makeText(this, "Please select at least one category.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteFilteredHistory(deleteSpam, deleteScam);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFilteredHistory(boolean deleteSpam, boolean deleteScam) {
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

        scansRef.whereIn("label", labels)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        Toast.makeText(this, "No matching history to delete.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : query) {
                        batch.update(doc.getReference(), "hiddenInHistory", true);
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(this, "Filtered history cleared.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed to delete filtered history.", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to fetch history.", Toast.LENGTH_SHORT).show());
    }
}
