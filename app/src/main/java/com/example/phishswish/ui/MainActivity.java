package com.example.phishswish.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.phishswish.R;
import com.example.phishswish.utils.SafeClickListener;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Date;

public class MainActivity extends BaseActivity {

    private ProgressBar logoutProgress;
    private ProgressBar navProgress;
    private BottomNavigationView bottomNav;
    private View mainContent;
    private GoogleSignInClient googleClient;
    private TextView tvOnline;
    private TextView tvScansCount;
    private TextView tvSafeCount, tvSpamCount, tvScamCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        mainContent = findViewById(R.id.main_content);
        logoutProgress = findViewById(R.id.logout_progress);
        navProgress = findViewById(R.id.nav_progress);
        tvOnline = findViewById(R.id.tvOnline);
        tvScansCount = findViewById(R.id.tvScansCount);
        tvSafeCount = findViewById(R.id.tvSafeCount);
        tvSpamCount = findViewById(R.id.tvSpamCount);
        tvScamCount = findViewById(R.id.tvScamCount);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btnPaste).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                startActivity(new Intent(MainActivity.this, PasteScanActivity.class));
                completion.onComplete();
            }
        });
        findViewById(R.id.btnInbox).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                startActivity(new Intent(MainActivity.this, InboxScanActivity.class));
                completion.onComplete();
            }
        });
        findViewById(R.id.btnHistory).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
                completion.onComplete();
            }
        });

        View ivAvatar = findViewById(R.id.ivAvatar);
        if (ivAvatar != null) {
            enableAvatarPreview(ivAvatar);
            ivAvatar.setOnClickListener(new SafeClickListener() {
                @Override
                public void onSafeClick(View v, Completion completion) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    completion.onComplete();
                }
            });
        }

        findViewById(R.id.btnLogout).setOnClickListener(new SafeClickListener() {
            @Override
            public void onSafeClick(View v, Completion completion) {
                showLogoutConfirmation();
                completion.onComplete();
            }
        });

        bottomNav = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setup(this, bottomNav, navProgress, R.id.nav_home);

        loadUserProfile(user);
        loadScansCountToday(user.getUid());
        loadCategoryCounts(user.getUid());
    }

    private void loadUserProfile(FirebaseUser user) {
        if (!isOnline()) return;
        ImageView ivAvatar = findViewById(R.id.ivAvatar);
        TextView tvGreeting = findViewById(R.id.tvGreeting);
        TextView tvName = findViewById(R.id.tvName);

        FirebaseFirestore.getInstance().collection("users")
                .document(user.getUid())
                .collection("profile")
                .document("info")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String username = doc.getString("username");
                        String profileImage = doc.getString("profileImage");

                        String display = (username != null && !username.isEmpty()) ? username : 
                                         ((user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ? user.getDisplayName() : 
                                         ((user.getEmail() != null) ? user.getEmail().split("@")[0] : "User"));
                        
                        if (tvGreeting != null) tvGreeting.setText("Hi, " + display + "! 👋");
                        if (tvName != null) tvName.setText(display);

                        if (profileImage != null && !profileImage.isEmpty() && ivAvatar != null) {
                            try {
                                byte[] bytes = Base64.decode(profileImage, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                ivAvatar.setImageBitmap(bitmap);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        String display = (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ? user.getDisplayName() : 
                                         ((user.getEmail() != null) ? user.getEmail().split("@")[0] : "User");
                        if (tvGreeting != null) tvGreeting.setText("Hi, " + display + "! 👋");
                        if (tvName != null) tvName.setText(display);
                    }
                });
    }

    private void loadScansCountToday(String uid) {
        if (!isOnline()) return;
        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        Date startDate = new Date(twentyFourHoursAgo);

        FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .collection("scans")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Boolean hidden = doc.getBoolean("hiddenInHistory");
                        if (hidden != null && hidden) continue;

                        Boolean hasAi = doc.getBoolean("hasAiAnalysis");
                        if (hasAi == null || !hasAi) continue;

                        count++;
                    }
                    tvScansCount.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> tvScansCount.setText("0"));
    }

    private void loadCategoryCounts(String uid) {
        if (!isOnline()) return;
        FirebaseFirestore.getInstance().collection("users")
                .document(uid)
                .collection("scans")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int safe = 0, spam = 0, scam = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Boolean hidden = doc.getBoolean("hiddenInHistory");
                        if (hidden != null && hidden) continue;

                        Boolean hasAi = doc.getBoolean("hasAiAnalysis");
                        if (hasAi == null || !hasAi) continue;

                        String label = doc.getString("label");
                        if (label != null) {
                            String normalized = label.trim().toLowerCase();
                            if (normalized.equals("safe")) safe++;
                            else if (normalized.equals("spam")) spam++;
                            else if (normalized.equals("scam")) scam++;
                        }
                    }
                    animateNumber(tvSafeCount, safe, "Safe ");
                    animateNumber(tvSpamCount, spam, "Spam ");
                    animateNumber(tvScamCount, scam, "Scam ");
                })
                .addOnFailureListener(e -> {
                    tvSafeCount.setText("Safe 0");
                    tvSpamCount.setText("Spam 0");
                    tvScamCount.setText("Scam 0");
                });
    }

    private void animateNumber(final TextView textView, int targetValue, final String prefix) {
        ValueAnimator animator = ValueAnimator.ofInt(0, targetValue);
        animator.setDuration(1000);
        animator.addUpdateListener(animation -> textView.setText(prefix + animation.getAnimatedValue().toString()));
        animator.start();
    }

    @Override
    protected void onOnlineStateChanged(boolean isOnline) {
        if (tvOnline == null) return;
        if (isOnline) {
            tvOnline.setText("Online");
            tvOnline.setTextColor(Color.parseColor("#16A34A"));
            tvOnline.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_dot_green, 0, 0, 0);
            tvOnline.setCompoundDrawablePadding(12);
            tvOnline.setBackgroundResource(R.drawable.bg_chip_online);
        } else {
            tvOnline.setText("Offline");
            tvOnline.setTextColor(Color.parseColor("#DC2626"));
            tvOnline.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_dot_red, 0, 0, 0);
            tvOnline.setCompoundDrawablePadding(12);
            tvOnline.setBackgroundResource(R.drawable.bg_chip_offline);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.onResume(this, bottomNav, navProgress, R.id.nav_home);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            loadUserProfile(user);
            loadScansCountToday(user.getUid());
            loadCategoryCounts(user.getUid());
        }
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
        if (logoutProgress != null) {
            logoutProgress.setElevation(20f); // Force it to the front
            logoutProgress.setVisibility(View.VISIBLE);
        }
        
        new Handler().postDelayed(() -> {
            FirebaseAuth.getInstance().signOut();
            googleClient.signOut().addOnCompleteListener(this, task -> {
                googleClient.revokeAccess().addOnCompleteListener(this, task2 -> {
                    goToLogin();
                });
            });
        }, 3000);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
