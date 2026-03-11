package com.example.phishswish.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.phishswish.R;
import com.example.phishswish.detector.Detector;
import com.example.phishswish.firebase.FirestoreService;
import com.example.phishswish.model.InboxMessage;
import com.example.phishswish.model.ScanResult;
import com.example.phishswish.ui.adapters.InboxAdapter;
import com.example.phishswish.utils.InboxConsentManager;
import com.example.phishswish.utils.SenderDetector;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Locale;

public class InboxScanActivity extends BaseActivity {

    private static final int REQ_READ_SMS = 2001;

    private RecyclerView rvInbox;
    private TextView tvInboxEmpty;
    private View btnRefreshInbox;
    private ImageButton btnMic;
    private ProgressBar pbRefreshing;
    private EditText etSearch;
    private TextView chipAll, chipUnread, chipLinks;
    private boolean isRefreshing = false;

    private final ArrayList<InboxMessage> items = new ArrayList<>();
    private InboxAdapter adapter;
    private BottomNavigationView bottomNav;

    private final ActivityResultLauncher<Intent> voiceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> spokenText = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (spokenText != null && !spokenText.isEmpty()) {
                        String query = spokenText.get(0);
                        if (etSearch != null) {
                            etSearch.setText(query);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inbox_scan);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        rvInbox = findViewById(R.id.rvInbox);
        tvInboxEmpty = findViewById(R.id.tvInboxEmpty);
        btnRefreshInbox = findViewById(R.id.btnRefreshInbox);
        btnMic = findViewById(R.id.btnMic);
        pbRefreshing = findViewById(R.id.pbRefreshing);
        etSearch = findViewById(R.id.etSearch);
        
        chipAll = findViewById(R.id.chipAll);
        chipUnread = findViewById(R.id.chipUnread);
        chipLinks = findViewById(R.id.chipLinks);
        
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnRefreshInbox != null) {
            btnRefreshInbox.setEnabled(false);
            btnRefreshInbox.setOnClickListener(v -> {
                if (isRefreshing) return;

                isRefreshing = true;
                btnRefreshInbox.setEnabled(false);
                if (pbRefreshing != null) pbRefreshing.setVisibility(View.VISIBLE);

                Toast.makeText(this, getString(R.string.msg_refreshing_inbox), Toast.LENGTH_SHORT).show();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    loadInbox();
                    if (pbRefreshing != null) pbRefreshing.setVisibility(View.GONE);
                    btnRefreshInbox.setEnabled(true);
                    isRefreshing = false;
                    Toast.makeText(this, getString(R.string.msg_inbox_up_to_date), Toast.LENGTH_SHORT).show();
                }, 2000);
            });
        }

        rvInbox.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InboxAdapter(items, msg -> showMessagePreview(msg));
        rvInbox.setAdapter(adapter);

        setupSearch();
        setupFilters();
        setupVoiceSearch();

        bottomNav = findViewById(R.id.bottom_navigation);
        BottomNavHelper.setup(this, bottomNav, null, R.id.nav_inbox);

        String uid = FirebaseAuth.getInstance().getUid();
        if (!InboxConsentManager.hasConsent(this, uid)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_allow_inbox_title))
                    .setMessage(getString(R.string.dialog_allow_inbox_message))
                    .setPositiveButton(getString(R.string.btn_allow), (dialog, which) -> {
                        InboxConsentManager.grantConsent(this, uid);
                        ensureSmsPermissionAndLoad();
                    })
                    .setNegativeButton(getString(R.string.btn_later), (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setCancelable(false)
                    .show();
        } else {
            ensureSmsPermissionAndLoad();
        }
    }

    @Override
    protected void onOnlineStateChanged(boolean isOnline) {
        super.onOnlineStateChanged(isOnline);
        if (btnRefreshInbox != null) {
            btnRefreshInbox.setEnabled(isOnline);
            btnRefreshInbox.setAlpha(isOnline ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavHelper.onResume(this, bottomNav, null, R.id.nav_inbox);
    }

    private void setupVoiceSearch() {
        if (btnMic != null) {
            btnMic.setOnClickListener(v -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.prompt_voice_search));
                try {
                    voiceLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.msg_voice_search_not_supported), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showMessagePreview(InboxMessage msg) {
        String dateStr = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                .format(new java.util.Date(msg.date));
        
        String previewContent = getString(R.string.label_sender_prefix) + (msg.address != null ? msg.address : getString(R.string.sender_unknown)) + "\n" +
                               getString(R.string.label_date) + ": " + dateStr + "\n\n" +
                               msg.body;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.title_inbox_scan))
                .setMessage(previewContent)
                .setPositiveButton(getString(R.string.btn_scan_now), (dialog, which) -> {
                    if (isOnline()) {
                        showScanConfirmation(msg);
                    } else {
                        requireInternetOrShowMessage(null);
                    }
                })
                .setNegativeButton(getString(R.string.btn_close), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void setupFilters() {
        if (chipAll != null) chipAll.setOnClickListener(v -> filterAll());
        if (chipUnread != null) chipUnread.setOnClickListener(v -> filterUnread());
        if (chipLinks != null) chipLinks.setOnClickListener(v -> filterLinks());
    }

    private void filterAll() {
        updateChipUI(chipAll, true);
        updateChipUI(chipUnread, false);
        updateChipUI(chipLinks, false);
        if (adapter != null) {
            adapter.setFilteredList(new ArrayList<>(adapter.getFullList()), false);
        }
    }

    private void filterUnread() {
        updateChipUI(chipUnread, true);
        updateChipUI(chipAll, false);
        updateChipUI(chipLinks, false);
        if (adapter != null) {
            ArrayList<InboxMessage> unread = new ArrayList<>();
            for (InboxMessage m : adapter.getFullList()) {
                if (!m.isRead) unread.add(m);
            }
            adapter.setFilteredList(unread, false);
        }
    }

    private void filterLinks() {
        updateChipUI(chipLinks, true);
        updateChipUI(chipAll, false);
        updateChipUI(chipUnread, false);
        if (adapter != null) {
            ArrayList<InboxMessage> withLinks = new ArrayList<>();
            for (InboxMessage m : adapter.getFullList()) {
                if (m.body != null && (m.body.toLowerCase().contains("http://") || m.body.toLowerCase().contains("https://"))) {
                    withLinks.add(m);
                }
            }
            adapter.setFilteredList(withLinks, true);
        }
    }

    private void updateChipUI(TextView chip, boolean isSelected) {
        if (chip == null) return;
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    private void setupSearch() {
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (adapter != null) {
                        adapter.filter(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void ensureSmsPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            if (btnRefreshInbox != null) btnRefreshInbox.setEnabled(true);
            loadInbox();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_SMS},
                    REQ_READ_SMS
            );
        }
    }

    private void loadInbox() {
        items.clear();
        Uri uri = Telephony.Sms.Inbox.CONTENT_URI;
        String[] projection = new String[]{
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
        };
        String sortOrder = Telephony.Sms.DATE + " DESC";

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idxAddress = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                int idxBody = cursor.getColumnIndex(Telephony.Sms.BODY);
                int idxDate = cursor.getColumnIndex(Telephony.Sms.DATE);
                int idxRead = cursor.getColumnIndex(Telephony.Sms.READ);

                while (cursor.moveToNext() && items.size() < 200) {
                    String address = SenderDetector.normalize(this, idxAddress >= 0 ? cursor.getString(idxAddress) : null);
                    String body = idxBody >= 0 ? cursor.getString(idxBody) : "";
                    long date = idxDate >= 0 ? cursor.getLong(idxDate) : System.currentTimeMillis();
                    int readVal = idxRead >= 0 ? cursor.getInt(idxRead) : 1;
                    boolean isRead = readVal == 1;

                    if (body != null && body.trim().length() > 0) {
                        items.add(new InboxMessage(address, body, date, isRead));
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_failed_read_inbox), Toast.LENGTH_SHORT).show();
        }

        if (adapter != null) {
            adapter.updateList(items);
        }
        
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (items.isEmpty()) {
            if (tvInboxEmpty != null) tvInboxEmpty.setVisibility(View.VISIBLE);
            rvInbox.setVisibility(View.GONE);
        } else {
            if (tvInboxEmpty != null) tvInboxEmpty.setVisibility(View.GONE);
            rvInbox.setVisibility(View.VISIBLE);
        }
    }

    private void showScanConfirmation(InboxMessage message) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_confirm_scan_title))
                .setMessage(getString(R.string.dialog_confirm_scan_message))
                .setPositiveButton(getString(R.string.btn_scan), (dialog, which) ->
                        requireInternetOrShowMessage(() -> performScanWithAnimation(message)))
                .setNegativeButton(getString(R.string.btn_cancel), (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void performScanWithAnimation(InboxMessage message) {
        if (!isOnline()) {
            requireInternetOrShowMessage(null);
            return;
        }
        // Minimal 2-second scanning animation
        if (pbRefreshing != null) pbRefreshing.setVisibility(View.VISIBLE);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (pbRefreshing != null) pbRefreshing.setVisibility(View.GONE);
            openResultForScan(message);
        }, 2000);
    }

    private void openResultForScan(InboxMessage message) {
        String messageBody = (message != null && message.body != null) ? message.body : "";
        messageBody = messageBody.trim();

        if (messageBody.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_empty_message_scan), Toast.LENGTH_SHORT).show();
            return;
        }

        if (messageBody.length() > 1000) {
            messageBody = messageBody.substring(0, 1000);
        }

        // Safe logging
        String safeSnippetLog = messageBody.substring(0, Math.min(30, messageBody.length()));
        Log.d("SCAN", "snippet=" + safeSnippetLog);

        // Rule-based analysis only; Gemini will be the single source of truth for final verdict.
        ScanResult ruleResult = Detector.analyze(messageBody);

        long ts = ruleResult.getTimestamp();
        if (ts == 0) ts = System.currentTimeMillis();
        
        // Hard rule: Store snippet only (max 120 chars)
        String snippet = messageBody.replace("\n", " ").replace("\r", " ").trim();
        if (snippet.length() > 120) snippet = snippet.substring(0, 120);

        String extractedUrl = (ruleResult.getUrls() != null && !ruleResult.getUrls().isEmpty())
                ? ruleResult.getUrls().get(0)
                : "";

        String sender = SenderDetector.normalize(this, message != null ? message.address : null);

        if (isOnline()) {
            FirestoreService.autoSaveScan(
                    snippet,
                    ruleResult.getLabel(),
                    (int) ruleResult.getConfidence(),
                    new java.util.ArrayList<>(),
                    ts,
                    extractedUrl,
                    null,
                    null,
                    sender,
                    "inbox"
            );
        }

        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("scan_result", ruleResult);
        intent.putExtra("sender", sender);
        intent.putExtra("source", "inbox");

        // ML-based fields removed; Gemini will own the final verdict.
        intent.putExtra("explanation", "");

        navigateWithLoading(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_SMS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (btnRefreshInbox != null) btnRefreshInbox.setEnabled(true);
                loadInbox();
            } else {
                Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        }
    }
}
