package com.example.phishswish.ui;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.phishswish.BuildConfig;
import com.example.phishswish.R;
import com.example.phishswish.detector.GeminiClient;

import com.example.phishswish.detector.VirusTotalClient;
import com.example.phishswish.detector.VtParser;
import com.example.phishswish.firebase.FirestoreService;
import com.example.phishswish.model.GeminiAnalysisResult;
import com.example.phishswish.model.ScanResult;
import com.example.phishswish.utils.LanguageManager;
import com.example.phishswish.utils.PercentageUtils;
import com.example.phishswish.utils.UrlExtractor;
import com.example.phishswish.utils.SenderDetector;
import com.example.phishswish.utils.VtCache;
import com.example.phishswish.utils.GeminiKeyManager;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import android.content.Context;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResultActivity extends BaseActivity {

    private enum UiState {
        LOADING,
        SUCCESS,
        ERROR
    }

    private TextView tvLabel, tvConfidence, tvReasons, tvRecommendation, tvUrl, tvSnippet, tvLinkStatus, tvSender;
    private LinearLayout layoutUrls, layoutNoUrls;
    private Button btnCopyLink, btnOpenLink, btnBack;
    private ScanResult scanResult;
    private UiState currentState = UiState.LOADING;

    private CardView cvResultCard;
    private CircularProgressIndicator cpConfidenceRing;
    private FrameLayout flShieldIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_result);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        tvLabel = findViewById(R.id.tvLabel);
        tvConfidence = findViewById(R.id.tvConfidence);
        tvReasons = findViewById(R.id.tvReasons);
        tvUrl = findViewById(R.id.tvUrl);
        tvSnippet = findViewById(R.id.tvSnippet);
        tvLinkStatus = findViewById(R.id.tvLinkStatus);
        tvSender = findViewById(R.id.tvSender);
        layoutUrls = findViewById(R.id.layoutUrls);
        layoutNoUrls = findViewById(R.id.layoutNoUrls);
        btnCopyLink = findViewById(R.id.btnCopyLink);
        btnOpenLink = findViewById(R.id.btnOpenLink);
        btnBack = findViewById(R.id.btnBack);

        cvResultCard = findViewById(R.id.cvResultCard);
        cpConfidenceRing = findViewById(R.id.cpConfidenceRing);
        flShieldIcon = findViewById(R.id.flShieldIcon);

        findViewById(R.id.btnBackHeader).setOnClickListener(v -> finish());
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        boolean fromHistory = getIntent().getBooleanExtra("fromHistory", false);

        String sender = SenderDetector.normalize(this, getIntent().getStringExtra("sender"));
        if (tvSender != null) {
            tvSender.setText(getString(R.string.label_sender_prefix) + sender);
        }

        if (fromHistory) {
            handleHistoryMode();
        } else {
            scanResult = (ScanResult) getIntent().getSerializableExtra("scan_result");
            if (scanResult != null) {
                if (scanResult.getUrls() == null) scanResult.setUrls(new ArrayList<>());

                showLoading();
                if (tvSnippet != null && scanResult.getSnippet() != null) {
                    tvSnippet.setText(scanResult.getSnippet());
                }

                String messageText = scanResult.getSnippet();
                String extractedUrl = null;
                if (!scanResult.getUrls().isEmpty()) {
                    extractedUrl = scanResult.getUrls().get(0);
                } else {
                    extractedUrl = UrlExtractor.extractFirstUrl(messageText);
                    if (extractedUrl != null && !extractedUrl.isEmpty()) {
                        scanResult.getUrls().add(extractedUrl);
                    }
                }
                if (extractedUrl != null && !extractedUrl.trim().isEmpty()) {
                    checkLink(extractedUrl);
                }

                // Trigger Gemini AI Analysis for new scan
                runGeminiAi(scanResult);
            } else {
                Toast.makeText(this, getString(R.string.msg_no_result), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onOnlineStateChanged(boolean isOnline) {
        super.onOnlineStateChanged(isOnline);
        if (isOnline && currentState == UiState.ERROR) {
            // Auto-retry if we were in error state
            if (scanResult != null && !getIntent().getBooleanExtra("fromHistory", false)) {
                runGeminiAi(scanResult);
            }
        }
    }

    private void runGeminiAi(ScanResult result) {
        if (result == null) return;
        showLoading();
        if (!isOnline()) {
            showError(getString(R.string.error_ai_internet));
            return;
        }

        GeminiKeyManager keyManager = new GeminiKeyManager();
        if (keyManager.getKeyCount() == 0) {
            showError(getString(R.string.error_ai_config));
            return;
        }

        String msg = result.getSnippet() != null ? result.getSnippet() : "";
        if (msg.length() > 600) msg = msg.substring(0, 600) + "...";

        String sender = SenderDetector.normalize(this, getIntent().getStringExtra("sender"));

        String label = result.getLabel() != null ? result.getLabel() : "UNKNOWN";
        int conf = PercentageUtils.toInt(result.getConfidence());

        String url = "";
        if (result.getUrls() != null && !result.getUrls().isEmpty()) {
            url = result.getUrls().get(0);
        }

        String urlStatus = (tvLinkStatus != null) ? tvLinkStatus.getText().toString() : "Link Status: UNKNOWN";

        String prompt =
                "You are a cybersecurity message classification engine for a mobile scam detection app.\n\n" +
                "Your task is to analyze a message using deep contextual reasoning and classify it strictly into ONE of these three categories:\n\n" +
                "SAFE\n" +
                "SPAM\n" +
                "SCAM\n\n" +
                "You must analyze using the following criteria:\n\n" +
                "1) Sender Analysis\n" +
                "- Is the sender numeric, named, or official shortcode?\n" +
                "- Does the sender name match known financial institutions or services?\n" +
                "- Could the sender be spoofed?\n" +
                "- Is the sender format suspicious?\n\n" +
                "2) Tone & Intent Analysis\n" +
                "- Urgency or fear tactics? (e.g., \"act now\", \"account locked\", \"final warning\")\n" +
                "- Financial bait? (e.g., \"you won\", \"received money\", \"claim reward\")\n" +
                "- Credential harvesting? (OTP, PIN, password requests)\n" +
                "- Authority impersonation? (bank, GCash, government, delivery services)\n" +
                "- Casual marketing tone vs coercive manipulation?\n\n" +
                "3) Structural & Technical Signals\n" +
                "- Presence of links (especially shortened URLs like bit.ly, tinyurl, etc.)\n" +
                "- Suspicious domains\n" +
                "- Requests to click external login pages\n" +
                "- Grammar anomalies\n" +
                "- Overly generic phrasing\n" +
                "- OTP format consistency (real OTP messages usually contain no clickable links)\n\n" +
                "4) Context Awareness\n" +
                "- Legitimate system OTPs include \"never share this code\" warnings.\n" +
                "- A legitimate OTP is SAFE only if:\n" +
                "   - No link included\n" +
                "   - No request to share OTP\n" +
                "   - Tone is neutral system-generated\n" +
                "- If a message claims you received money AND includes a link → likely SCAM.\n" +
                "- Marketing promotions without credential theft → SPAM.\n" +
                "- Messages attempting to steal money, credentials, or identity → SCAM.\n\n" +
                "Classification Definitions:\n\n" +
                "SAFE:\n" +
                "Legitimate expected communication from a trusted or system-generated source.\n\n" +
                "SPAM:\n" +
                "Unwanted promotional, bulk, or marketing messages without direct fraud attempt.\n\n" +
                "SCAM:\n" +
                "Fraudulent attempt to steal money, identity, login credentials, or mislead the user through deception.\n\n" +
                "IMPORTANT RULES:\n" +
                "- Do NOT overclassify as SCAM without strong evidence.\n" +
                "- Do NOT classify as SAFE if suspicious links exist.\n" +
                "- Sender name alone does NOT guarantee safety.\n" +
                "- Use weighted reasoning across all signals.\n" +
                "- Avoid bias.\n" +
                "- Be conservative but security-aware.\n\n" +
                "Return STRICT JSON ONLY in this format:\n\n" +
                "{\n" +
                "  \"category\": \"SAFE | SPAM | SCAM\",\n" +
                "  \"certainty\": 0-100,\n" +
                "  \"why\": \"short explanation of why the message is classified this way\"\n" +
                "}\n\n" +
                "Rules:\n" +
                "- certainty must be integer 0-100.\n" +
                "- category must be exactly SAFE, SPAM, or SCAM.\n" +
                "- No extra commentary.\n" +
                "- No markdown.\n" +
                "- No explanation outside JSON.\n\n" +
                "Now analyze the following:\n\n" +
                "Sender: " + sender + "\n" +
                "Message: " + msg + "\n";

        // Append language instruction so Gemini returns reasoning in the selected language.
        String appLang = LanguageManager.getLanguage(this);
        if ("tl".equals(appLang)) {
            prompt += "\nIMPORTANT: Write the \"why\" field in Filipino/Tagalog. " +
                      "Keep \"category\" and \"certainty\" values exactly as specified (SAFE/SPAM/SCAM, integer).\n";
        } else {
            prompt += "\nIMPORTANT: Write the \"why\" field in English.\n";
        }


        GeminiClient client = new GeminiClient();
        client.analyzeWithFallback(keyManager.getCurrentKey(), keyManager, prompt, new GeminiClient.Callback() {
            @Override
            public void onSuccess(String analysis, String recommendation) {
                // analysis now contains the raw JSON payload from Gemini.
                try {
                    final GeminiAnalysisResult gemini = GeminiAnalysisResult.fromJson(analysis);
                    runOnUiThread(() -> {
                        showSuccess(gemini);
                    });
                } catch (Exception e) {
                    Log.e("Gemini", "Failed to parse Gemini JSON", e);
                    Log.e("Gemini", "Raw Gemini output:\n" + analysis);
                    runOnUiThread(() ->
                            showError(getString(R.string.error_ai_failed))
                    );
                }
            }

            @Override
            public void onError(String error) {
                Log.e("Gemini", "Gemini failed: " + error);
                runOnUiThread(() ->
                        showError(getString(R.string.error_ai_failed))
                );
            }
        });
    }

    private String getStaticRecommendation() {
        return getString(R.string.msg_static_recommendation);
    }

    /**
     * Single source of truth: drive ALL verdict UI from GeminiAnalysisResult.
     */
    private void renderResult(GeminiAnalysisResult result) {
        if (result == null) return;

        String verdict = result.getCategory().name(); // SAFE/SPAM/SCAM
        int certainty = PercentageUtils.clampInt(result.getCertainty());

        // Ensure theming is always consistent with category.
        if (tvLabel != null) {
            tvLabel.setText(getString(R.string.label_verdict, verdict));
            tvLabel.setVisibility(View.VISIBLE);
        }
        applyTheme(verdict);

        // Donut percent animation from certainty only.
        if (cpConfidenceRing != null) {
            cpConfidenceRing.setVisibility(View.VISIBLE);
        }
        animateResult(certainty);

        if (tvConfidence != null) {
            // Keep a clean, centered percentage inside the donut.
            tvConfidence.setText(
                    String.format(Locale.getDefault(), "%d%%", certainty)
            );
            tvConfidence.setVisibility(View.VISIBLE);
        }

        if (tvReasons != null) {
            tvReasons.setText(result.getWhy());
            tvReasons.setVisibility(View.VISIBLE);
        }

        if (tvRecommendation != null) {
            tvRecommendation.setText(getStaticRecommendation());
        }

        // Check original ScanResult URLs so that the URL section and VirusTotal
        // status remain visible whenever a URL is present.
        String firstUrl = null;
        if (scanResult != null
                && scanResult.getUrls() != null
                && !scanResult.getUrls().isEmpty()) {
            firstUrl = scanResult.getUrls().get(0);
        }

        if (firstUrl != null && !firstUrl.trim().isEmpty()) {
            String urlForUi = firstUrl.trim();

            if (layoutUrls != null) layoutUrls.setVisibility(View.VISIBLE);
            if (layoutNoUrls != null) layoutNoUrls.setVisibility(View.GONE);
            if (tvUrl != null) tvUrl.setText(urlForUi);

            if (btnCopyLink != null) {
                btnCopyLink.setOnClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Scanned URL", urlForUi);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, getString(R.string.msg_url_copied), Toast.LENGTH_SHORT).show();
                });
            }
            if (btnOpenLink != null) {
                btnOpenLink.setOnClickListener(v -> showSecurityWarning(urlForUi));
            }
        } else {
            // No URL detected anywhere: show the "no urls" card.
            if (layoutUrls != null) layoutUrls.setVisibility(View.GONE);
            if (layoutNoUrls != null) layoutNoUrls.setVisibility(View.VISIBLE);
        }
    }

    private void animateResult(int confidence) {
        int safeConfidence = PercentageUtils.clampInt(confidence);
        ValueAnimator progressAnimator = ValueAnimator.ofInt(0, safeConfidence);
        progressAnimator.setDuration(1100);
        progressAnimator.setInterpolator(new DecelerateInterpolator());
        progressAnimator.addUpdateListener(animation -> {
            if (cpConfidenceRing != null) cpConfidenceRing.setProgress((int) animation.getAnimatedValue());
            // Text label is handled in renderResult as "CERTAINTY: NN%".
        });
        progressAnimator.start();
    }

    private void showLoading() {
        currentState = UiState.LOADING;

        // Neutral colors when there is no verdict yet.
        applyNeutralTheme();

        if (cpConfidenceRing != null) {
            cpConfidenceRing.setProgress(0);
            cpConfidenceRing.setVisibility(View.INVISIBLE);
        }
        if (tvConfidence != null) {
            tvConfidence.setVisibility(View.INVISIBLE);
        }
        if (tvLabel != null) {
            tvLabel.setVisibility(View.INVISIBLE);
        }
        if (tvReasons != null) {
            tvReasons.setText(getString(R.string.msg_analyzing_ai));
            tvReasons.setVisibility(View.VISIBLE);
        }
        if (tvRecommendation != null) {
            tvRecommendation.setVisibility(View.GONE);
        }
        if (layoutUrls != null) layoutUrls.setVisibility(View.GONE);
        if (layoutNoUrls != null) layoutNoUrls.setVisibility(View.GONE);
    }

    private void showSuccess(GeminiAnalysisResult result) {
        currentState = UiState.SUCCESS;

        // Auto-save using Gemini as the single source of truth.
        boolean fromHistory = getIntent().getBooleanExtra("fromHistory", false);
        if (!fromHistory && scanResult != null && result != null) {
            scanResult.setLabel(result.getCategory().name());
            scanResult.setConfidence(result.getCertainty());
            autoSaveToCloud(scanResult, result.getWhy());
            com.example.phishswish.utils.DailyScanTracker.increment(this);
        }

        renderResult(result);
    }

    private void showError() {
        showError(getString(R.string.msg_ai_unavailable));
    }

    private void showError(String message) {
        currentState = UiState.ERROR;

        // Neutral colors when there is no valid verdict.
        applyNeutralTheme();

        if (cpConfidenceRing != null) {
            cpConfidenceRing.setVisibility(View.INVISIBLE);
        }
        if (tvConfidence != null) {
            tvConfidence.setVisibility(View.INVISIBLE);
        }
        if (tvLabel != null) {
            tvLabel.setVisibility(View.INVISIBLE);
        }
        if (layoutUrls != null) layoutUrls.setVisibility(View.GONE);
        if (layoutNoUrls != null) layoutNoUrls.setVisibility(View.GONE);

        if (tvReasons != null) {
            tvReasons.setText(message);
            tvReasons.setVisibility(View.VISIBLE);
        }
        if (tvRecommendation != null) {
            tvRecommendation.setText("");
            tvRecommendation.setVisibility(View.GONE);
        }
    }

    private void applyTheme(String label) {
        int themeColor;
        int cardBg;
        int pillBg;

        if ("SAFE".equalsIgnoreCase(label)) {
            themeColor = ContextCompat.getColor(this, R.color.safe_blue);
            cardBg = ContextCompat.getColor(this, R.color.result_safe_bg);
            pillBg = R.drawable.bg_pill_safe;
        } else if ("SPAM".equalsIgnoreCase(label) || "SUSPICIOUS".equalsIgnoreCase(label)) {
            themeColor = ContextCompat.getColor(this, R.color.spam_orange);
            cardBg = ContextCompat.getColor(this, R.color.result_spam_bg);
            pillBg = R.drawable.bg_pill_suspicious;
        } else {
            themeColor = ContextCompat.getColor(this, R.color.scam_red);
            cardBg = ContextCompat.getColor(this, R.color.result_scam_bg);
            pillBg = R.drawable.bg_pill_malicious;
        }

        if (cvResultCard != null) cvResultCard.setCardBackgroundColor(cardBg);
        if (cpConfidenceRing != null) cpConfidenceRing.setIndicatorColor(themeColor);
        if (tvLabel != null) tvLabel.setBackgroundResource(pillBg);
        
        if (flShieldIcon != null && flShieldIcon.getChildAt(0) instanceof ImageView) {
            ((ImageView) flShieldIcon.getChildAt(0)).setColorFilter(themeColor);
        }
    }

    /**
     * Neutral visual style used when there is no verdict yet (loading/error).
     */
    private void applyNeutralTheme() {
        int neutralCardBg = Color.parseColor("#FFFFFF");
        int neutralColor = Color.parseColor("#6B7280"); // gray-600

        if (cvResultCard != null) cvResultCard.setCardBackgroundColor(neutralCardBg);
        if (cpConfidenceRing != null) cpConfidenceRing.setIndicatorColor(neutralColor);

        if (flShieldIcon != null && flShieldIcon.getChildAt(0) instanceof ImageView) {
            ((ImageView) flShieldIcon.getChildAt(0)).setColorFilter(neutralColor);
        }
    }

    private void checkLink(String url) {
        if (!isOnline()) {
            if (tvLinkStatus != null) tvLinkStatus.setText(getString(R.string.label_link_status, getString(R.string.status_no_internet)));
            return;
        }
        String urlForVirusTotal = (url != null) ? url.trim() : "";

        String cached = VtCache.get(this, urlForVirusTotal);
        if (cached != null) {
            try {
                VtParser.Result result = VtParser.parse(urlForVirusTotal, cached);
                updateLinkUi(result.status);
                return;
            } catch (Exception ignored) {}
        }

        if (tvLinkStatus != null) {
            tvLinkStatus.setText(getString(R.string.label_link_status, getString(R.string.status_checking)));
            tvLinkStatus.setTextColor(Color.GRAY);
        }

        final String finalUrl = urlForVirusTotal;
        new Thread(() -> {
            try {
                VirusTotalClient client = new VirusTotalClient(BuildConfig.VT_API_KEY);
                String json = client.getUrlReport(finalUrl);
                VtCache.save(this, finalUrl, json);
                VtParser.Result result = VtParser.parse(finalUrl, json);
                String normalized = result.status != null ? result.status.toLowerCase(java.util.Locale.ROOT) : "unknown";
                runOnUiThread(() -> updateLinkUi(normalized));
            } catch (Exception e) {
                runOnUiThread(() -> updateLinkUi("unknown"));
            }
        }).start();
    }

    private void updateLinkUi(String status) {
        if (tvLinkStatus == null || status == null) return;
        
        String cleanStatus = status.trim().toLowerCase(java.util.Locale.ROOT);
        if (!cleanStatus.equals("safe") && !cleanStatus.equals("harmful")) {
            cleanStatus = "unknown";
        }
        
        tvLinkStatus.setVisibility(View.VISIBLE);
        String displayStatus = "";
        if ("safe".equals(cleanStatus)) displayStatus = getString(R.string.status_safe);
        else if ("harmful".equals(cleanStatus)) displayStatus = getString(R.string.status_harmful);
        else displayStatus = getString(R.string.status_unknown);
        
        tvLinkStatus.setText(getString(R.string.label_link_status, displayStatus));
        
        if ("safe".equals(cleanStatus)) {
            tvLinkStatus.setTextColor(Color.parseColor("#16A34A"));
        } else if ("harmful".equals(cleanStatus)) {
            tvLinkStatus.setTextColor(Color.parseColor("#DC2626"));
        } else {
            tvLinkStatus.setTextColor(Color.GRAY);
        }
        
        boolean fromHistory = getIntent().getBooleanExtra("fromHistory", false);
        if (!fromHistory && scanResult != null) {
            com.example.phishswish.firebase.FirestoreService.updateLinkStatus(String.valueOf(scanResult.getTimestamp()), cleanStatus);
        }
    }

    private void autoSaveToCloud(ScanResult result, String aiAnalysis) {
        if (!isOnline()) return;
        long ts = result.getTimestamp();
        if (ts == 0) {
            ts = System.currentTimeMillis();
            result.setTimestamp(ts);
        }
        String snippet = result.getSnippet();
        String extractedUrl = (result.getUrls() != null && !result.getUrls().isEmpty())
                ? result.getUrls().get(0) : "";
        String sender = SenderDetector.normalize(this, getIntent().getStringExtra("sender"));

    String source = getIntent().getStringExtra("source");

    FirestoreService.autoSaveScan(snippet, result.getLabel(), (int) result.getConfidence(),
            new ArrayList<>(), ts, extractedUrl, aiAnalysis, null, sender, source, result.getUrls());
    }

    private void handleHistoryMode() {
        // For history, we expect label/confidence to already reflect the saved Gemini verdict.
        String label = getIntent().getStringExtra("label");
        int confidence = PercentageUtils.clampInt(getIntent().getIntExtra("confidence", 0));
        String snippet = getIntent().getStringExtra("snippet");
        String extractedUrl = getIntent().getStringExtra("extractedUrl");

        if (tvSnippet != null && snippet != null) {
            tvSnippet.setText(snippet);
        }
        
        String linkStatus = getIntent().getStringExtra("link_status");
        if (linkStatus != null && !linkStatus.trim().isEmpty() && tvLinkStatus != null) {
            updateLinkUi(linkStatus);
        } else if (tvLinkStatus != null) {
            tvLinkStatus.setVisibility(View.GONE);
        }

        ArrayList<String> detectedLinks = getIntent().getStringArrayListExtra("detected_links");
        ArrayList<String> allLinks = new ArrayList<>();
        if (extractedUrl != null && !extractedUrl.isEmpty()) {
            allLinks.add(extractedUrl);
        }
        if (detectedLinks != null) {
            for (String link : detectedLinks) {
                if (!allLinks.contains(link)) {
                    allLinks.add(link);
                }
            }
        }

        scanResult = new ScanResult();
        scanResult.setUrls(allLinks);
        scanResult.setSnippet(snippet);
        scanResult.setLabel(label);
        scanResult.setConfidence(confidence);
        long ts = getIntent().getLongExtra("timestamp", 0);
        scanResult.setTimestamp(ts);

        try {
            GeminiAnalysisResult.Category cat = GeminiAnalysisResult.Category.valueOf(
                    label != null ? label.toUpperCase(Locale.ROOT) : "SPAM"
            );
            String aiAnalysis = getIntent().getStringExtra("aiAnalysis");
            GeminiAnalysisResult model = GeminiAnalysisResult.fromValues(
                    cat,
                    confidence,
                    aiAnalysis != null ? aiAnalysis : "", // Uses stored AI explanation
                    new ArrayList<>(),
                    allLinks
            );
            showSuccess(model);
        } catch (Exception e) {
            showError(getString(R.string.error_ai_entry_unavailable));
        }
    }

    private void showSecurityWarning(String url) {
        String statusText = (tvLinkStatus != null) ? tvLinkStatus.getText().toString() : getString(R.string.label_link_status, getString(R.string.status_unknown));
        int statusColor = (tvLinkStatus != null) ? tvLinkStatus.getCurrentTextColor() : Color.GRAY;

        String rawMsg = getString(R.string.msg_confirm_link_open, statusText);
        SpannableString spannableMessage = new SpannableString(rawMsg);
        int start = rawMsg.indexOf(statusText);
        if (start != -1) {
            spannableMessage.setSpan(new ForegroundColorSpan(statusColor), start, start + statusText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_confirm_link_open_title))
                .setMessage(spannableMessage)
                .setPositiveButton(getString(R.string.btn_yes_open), (dialog, which) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.msg_cannot_open_url), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_no), (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
