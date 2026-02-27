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
import com.example.phishswish.detector.GeminiModelsDebug;
import com.example.phishswish.detector.VirusTotalClient;
import com.example.phishswish.detector.VtParser;
import com.example.phishswish.firebase.FirestoreService;
import com.example.phishswish.model.GeminiAnalysisResult;
import com.example.phishswish.model.ScanResult;
import com.example.phishswish.utils.PercentageUtils;
import com.example.phishswish.utils.UrlExtractor;
import com.example.phishswish.utils.VtCache;
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

        String sender = getIntent().getStringExtra("sender");
        if (sender == null || sender.trim().isEmpty()) {
            sender = "Unknown";
        }
        if (tvSender != null) {
            tvSender.setText("Sender: " + sender);
        }

        if (fromHistory) {
            handleHistoryMode();
        } else {
            scanResult = (ScanResult) getIntent().getSerializableExtra("scan_result");
            if (scanResult != null) {
                if (scanResult.getIndicators() == null) scanResult.setIndicators(new ArrayList<>());
                if (scanResult.getUrls() == null) scanResult.setUrls(new ArrayList<>());

                showLoading();
                if (tvSnippet != null && scanResult.getSnippet() != null) {
                    tvSnippet.setText(scanResult.getSnippet());
                }

                String messageText = scanResult.getSnippet();
                String extractedUrl = UrlExtractor.extractFirstUrl(messageText);
                if (extractedUrl != null) {
                    checkLink(extractedUrl);
                }

                // Trigger Gemini AI Analysis for new scan
                runGeminiAi(scanResult);
            } else {
                Toast.makeText(this, "No result data found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void runGeminiAi(ScanResult result) {
        if (result == null) return;
        showLoading();
        if (!isOnline()) {
            showError("AI analysis requires an internet connection. Please reconnect and try again.");
            return;
        }

        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            showError("AI Analysis is not configured properly. Please contact support.");
            return;
        }

        String msg = result.getSnippet() != null ? result.getSnippet() : "";
        if (msg.length() > 600) msg = msg.substring(0, 600) + "...";

        String sender = getIntent().getStringExtra("sender");
        if (sender == null || sender.trim().isEmpty()) {
            sender = "Unknown";
        }

        String label = result.getLabel() != null ? result.getLabel() : "UNKNOWN";
        int conf = PercentageUtils.toInt(result.getConfidence());

        String url = "";
        if (result.getUrls() != null && !result.getUrls().isEmpty()) {
            url = result.getUrls().get(0);
        }

        String urlStatus = (tvLinkStatus != null) ? tvLinkStatus.getText().toString() : "Link Status: UNKNOWN";

        String indicators = "";
        if (result.getIndicators() != null && !result.getIndicators().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : result.getIndicators()) sb.append("- ").append(s).append("\n");
            indicators = sb.toString();
        } else {
            indicators = "- None detected\n";
        }

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
                "  \"risk_level\": \"LOW | MEDIUM | HIGH\",\n" +
                "  \"reasoning\": \"Clear concise explanation (2-4 sentences)\",\n" +
                "  \"detected_signals\": [\n" +
                "    \"signal 1\",\n" +
                "    \"signal 2\"\n" +
                "  ]\n" +
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

        GeminiClient client = new GeminiClient();
        client.analyze(BuildConfig.GEMINI_API_KEY, prompt, new GeminiClient.Callback() {
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
                            showError("Unable to analyze right now. Please try again.")
                    );
                }
            }

            @Override
            public void onError(String error) {
                Log.e("Gemini", "Gemini failed: " + error);
                runOnUiThread(() ->
                        showError("Unable to analyze right now. Please try again.")
                );
            }
        });
    }

    private String joinTips(GeminiAnalysisResult gemini) {
        if (gemini == null || gemini.getTips() == null || gemini.getTips().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String tip : gemini.getTips()) {
            sb.append("• ").append(tip.trim()).append("\n");
        }
        return sb.toString().trim();
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
            tvLabel.setText(String.format(Locale.getDefault(), "VERDICT: %s", verdict));
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
            String joined = joinTips(result);
            tvRecommendation.setText(joined.isEmpty()
                    ? "Stay cautious and verify the sender before acting."
                    : joined);
        }

        // Use Gemini-detected links if available; otherwise fall back to the
        // original ScanResult URLs so that the URL section and VirusTotal
        // status remain visible whenever a URL is present.
        String firstUrl = null;
        if (result.getDetectedLinks() != null && !result.getDetectedLinks().isEmpty()) {
            firstUrl = result.getDetectedLinks().get(0);
        } else if (scanResult != null
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
                    Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
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
            tvReasons.setText("Analyzing with AI...");
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
        if (scanResult != null && result != null) {
            scanResult.setLabel(result.getCategory().name());
            scanResult.setConfidence(result.getCertainty());
            autoSaveToCloud(scanResult, result.getWhy(), joinTips(result));
        }

        renderResult(result);
    }

    private void showError() {
        showError("Unable to analyze right now. Please try again.");
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
            themeColor = Color.parseColor("#16A34A");
            cardBg = Color.parseColor("#E7F9EF");
            pillBg = R.drawable.bg_pill_safe;
        } else if ("SPAM".equalsIgnoreCase(label) || "SUSPICIOUS".equalsIgnoreCase(label)) {
            themeColor = Color.parseColor("#F59E0B");
            cardBg = Color.parseColor("#FFFBEB");
            pillBg = R.drawable.bg_pill_suspicious;
        } else {
            themeColor = Color.parseColor("#DC2626");
            cardBg = Color.parseColor("#FEE2E2");
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
            if (tvLinkStatus != null) tvLinkStatus.setText("Link Status: Unknown (no internet)");
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
            tvLinkStatus.setText("Link Status: Checking...");
            tvLinkStatus.setTextColor(Color.GRAY);
        }

        final String finalUrl = urlForVirusTotal;
        new Thread(() -> {
            try {
                VirusTotalClient client = new VirusTotalClient(BuildConfig.VT_API_KEY);
                String json = client.getUrlReport(finalUrl);
                VtCache.save(this, finalUrl, json);
                VtParser.Result result = VtParser.parse(finalUrl, json);
                runOnUiThread(() -> updateLinkUi(result.status));
            } catch (Exception e) {
                runOnUiThread(() -> { if (tvLinkStatus != null) tvLinkStatus.setText("Link Status: UNKNOWN"); });
            }
        }).start();
    }

    private void updateLinkUi(String status) {
        if (tvLinkStatus == null) return;
        tvLinkStatus.setText("Link Status: " + status);
        if ("SAFE".equalsIgnoreCase(status)) {
            tvLinkStatus.setTextColor(Color.parseColor("#16A34A"));
        } else if ("HARMFUL".equalsIgnoreCase(status)) {
            tvLinkStatus.setTextColor(Color.parseColor("#DC2626"));
        } else {
            tvLinkStatus.setTextColor(Color.GRAY);
        }
    }

    private void autoSaveToCloud(ScanResult result, String aiAnalysis, String aiRecommendation) {
        if (!isOnline()) return;
        long ts = result.getTimestamp();
        if (ts == 0) ts = System.currentTimeMillis();
        String snippet = result.getSnippet();
        if (snippet != null && snippet.length() > 120) {
            snippet = snippet.substring(0, 117) + "...";
        }
        String extractedUrl = (result.getUrls() != null && !result.getUrls().isEmpty())
                ? result.getUrls().get(0) : "";

        FirestoreService.autoSaveScan(snippet, result.getLabel(), (int) result.getConfidence(), 
                result.getIndicators(), ts, extractedUrl, aiAnalysis, aiRecommendation);
    }

    private void handleHistoryMode() {
        // For history, we expect label/confidence to already reflect the saved Gemini verdict.
        String label = getIntent().getStringExtra("label");
        int confidence = PercentageUtils.clampInt(getIntent().getIntExtra("confidence", 0));
        String snippet = getIntent().getStringExtra("snippet");
        ArrayList<String> indicators = getIntent().getStringArrayListExtra("indicators");
        if (indicators == null) indicators = new ArrayList<>();
        String extractedUrl = getIntent().getStringExtra("extractedUrl");

        if (tvSnippet != null && snippet != null) {
            tvSnippet.setText(snippet);
        }
        if (extractedUrl != null && !extractedUrl.isEmpty()) {
            checkLink(extractedUrl);
        }

        try {
            GeminiAnalysisResult.Category cat = GeminiAnalysisResult.Category.valueOf(
                    label != null ? label.toUpperCase(Locale.ROOT) : "SPAM"
            );
            GeminiAnalysisResult model = GeminiAnalysisResult.fromValues(
                    cat,
                    confidence,
                    "", // explanation text is shown from saved aiAnalysis below
                    new ArrayList<>(),
                    extractedUrl != null && !extractedUrl.isEmpty()
                            ? java.util.Collections.singletonList(extractedUrl)
                            : new ArrayList<>()
            );
            showSuccess(model);
        } catch (Exception e) {
            showError("AI analysis for this entry is not available.");
        }

        // Override AI text from stored strings if present.
        String aiAnalysis = getIntent().getStringExtra("aiAnalysis");
        String aiRecommendation = getIntent().getStringExtra("aiRecommendation");
        if (aiAnalysis != null && !aiAnalysis.isEmpty() && tvReasons != null) {
            tvReasons.setText(aiAnalysis);
            tvReasons.setVisibility(View.VISIBLE);
        }
        if (aiRecommendation != null && !aiRecommendation.isEmpty() && tvRecommendation != null) {
            tvRecommendation.setText(aiRecommendation);
            tvRecommendation.setVisibility(View.VISIBLE);
        }
    }

    private void showSecurityWarning(String url) {
        String statusText = (tvLinkStatus != null) ? tvLinkStatus.getText().toString() : "Link Status: UNKNOWN";
        int statusColor = (tvLinkStatus != null) ? tvLinkStatus.getCurrentTextColor() : Color.GRAY;

        SpannableString spannableMessage = new SpannableString("Are you sure you want to open this link?\n\n" + statusText);
        int start = spannableMessage.toString().indexOf(statusText);
        if (start != -1) {
            spannableMessage.setSpan(new ForegroundColorSpan(statusColor), start, start + statusText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Link Open")
                .setMessage(spannableMessage)
                .setPositiveButton("Yes, Open", (dialog, which) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open URL", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}
