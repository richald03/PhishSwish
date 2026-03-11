package com.example.phishswish.ui;

import android.Manifest;
import android.graphics.Rect;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.phishswish.R;
import com.example.phishswish.model.ScanResult;
import com.example.phishswish.ui.views.ArPatternHighlighter;
import com.example.phishswish.ui.views.OcrOverlayView;
import com.example.phishswish.ui.views.OverlayItem;
import com.example.phishswish.utils.CameraScanTextParser;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArScanActivity extends BaseActivity {

    private static final String TAG = "ArScanActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;

    private PreviewView previewView;
    private ImageView frozenImageView;
    private OcrOverlayView ocrOverlay;
    private TextView tvStatus, tvTip;
    private android.widget.EditText tvDetectedText;
    private Button btnAnalyze, btnCopy;
    private ImageButton btnCapture, btnCancel;
    private View btnBack, guideFrame;
    private View floatingBubble;
    private TextView bubbleCopy, bubbleAnalyze;
    private View selectionDimOverlay;

    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private ProcessCameraProvider cameraProvider;

    private Text lastCapturedTextObj = null;
    private String selectedCustomText = "";
    private long lastAnalysisTime = 0;
    private boolean isProcessing = false;
    private boolean isFrozen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_scan);

        previewView = findViewById(R.id.previewView);
        frozenImageView = findViewById(R.id.frozenImageView);
        ocrOverlay = findViewById(R.id.ocrOverlay);
        tvStatus = findViewById(R.id.tvStatus);
        tvTip = findViewById(R.id.tvTip);
        tvDetectedText = findViewById(R.id.tvDetectedText);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnCopy = findViewById(R.id.btnCopy);
        btnCancel = findViewById(R.id.btnCancel);
        btnCapture = findViewById(R.id.btnCapture);
        btnBack = findViewById(R.id.btnBack);
        guideFrame = findViewById(R.id.guideFrame);
        floatingBubble = findViewById(R.id.floatingSelectionBubble);
        bubbleCopy = findViewById(R.id.bubbleCopy);
        bubbleAnalyze = findViewById(R.id.bubbleAnalyze);
        selectionDimOverlay = findViewById(R.id.selectionDimOverlay);

        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnBack.setOnClickListener(v -> finish());
        btnCapture.setOnClickListener(v -> enterFrozenMode());
        btnCancel.setOnClickListener(v -> exitFrozenMode());
        btnCopy.setOnClickListener(v -> copySelectedText());
        bubbleCopy.setOnClickListener(v -> copySelectedText());
        btnAnalyze.setOnClickListener(v -> showSenderConfirmationDialog());
        bubbleAnalyze.setOnClickListener(v -> showSenderConfirmationDialog());
        
        ocrOverlay.setOnBlockSelectedListener(new OcrOverlayView.OnBlockSelectedListener() {
            @Override
            public void onBlockSelectionChanged(String selectedText) {
                // If the user manually picks blocks, we update the editor with that selection.
                // But the default is now to grab everything on entry.
                if (!selectedText.isEmpty()) {
                    tvDetectedText.setText(selectedText);
                    btnAnalyze.setEnabled(true);
                    btnCopy.setEnabled(true);
                }
            }

            @Override
            public void onSelectionPositionChanged(android.graphics.RectF bounds) {
                if (bounds == null || selectedCustomText.isEmpty()) {
                    floatingBubble.setVisibility(View.GONE);
                } else {
                    floatingBubble.setVisibility(View.VISIBLE);
                    // Position bubble above the selection
                    // If selection is too high, position it below
                    float x = bounds.centerX() - (floatingBubble.getWidth() / 2f);
                    float y = bounds.top - floatingBubble.getHeight() - 40; // 40px gap
                    
                    if (y < 200) { // Near top of screen
                        y = bounds.bottom + 40;
                    }
                    
                    // Keep in screen bounds
                    x = Math.max(16, Math.min(x, previewView.getWidth() - floatingBubble.getWidth() - 16));
                    
                    floatingBubble.setX(x);
                    floatingBubble.setY(y);
                }
            }
        });

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        }

        guideFrame.post(this::updateScannerRectInOverlay);
        
        setupTextEditability();
    }

    private void setupTextEditability() {
        tvDetectedText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateDetectedTextEditability(s.toString());
            }
        });
        // Initial state
        updateDetectedTextEditability(tvDetectedText.getText().toString());
    }

    private void updateDetectedTextEditability(String text) {
        String placeholder = getString(R.string.msg_waiting_detection);
        boolean isPlaceholder = text.equals(placeholder);
        
        tvDetectedText.setFocusable(!isPlaceholder);
        tvDetectedText.setFocusableInTouchMode(!isPlaceholder);
        tvDetectedText.setCursorVisible(!isPlaceholder);
        
        if (isPlaceholder) {
            // Hide keyboard if it was open
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null && tvDetectedText.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(tvDetectedText.getWindowToken(), 0);
            }
            
            // Clear any link/span styling for placeholder
            tvDetectedText.setMovementMethod(null);
            tvDetectedText.setLinksClickable(false);
        } else {
            // Restore selection and link behavior when real text is present
            if (tvDetectedText.getMovementMethod() == null) {
                tvDetectedText.setMovementMethod(android.text.method.ArrowKeyMovementMethod.getInstance());
            }
            tvDetectedText.setLinksClickable(true);
        }
    }

    private void updateScannerRectInOverlay() {
        if (guideFrame == null || ocrOverlay == null) return;
        Rect rect = new Rect();
        rect.left = guideFrame.getLeft();
        rect.top = guideFrame.getTop();
        rect.right = guideFrame.getRight();
        rect.bottom = guideFrame.getBottom();
        ocrOverlay.setScannerRect(rect);
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, getString(R.string.msg_grant_camera_permission), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Performance: 960x540 resolution as requested
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(960, 540))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (isFrozen) {
            imageProxy.close();
            return;
        }

        long currentTime = System.currentTimeMillis();
        // Throttle OCR: ~250ms interval
        if (isProcessing || (currentTime - lastAnalysisTime < 250)) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        isProcessing = true;
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        // Set preview size for coordinate mapping in overlay
        // Note: For analysis, width/height might be swapped depending on rotation
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        if (rotation == 90 || rotation == 270) {
            ocrOverlay.setPreviewSize(imageProxy.getHeight(), imageProxy.getWidth());
        } else {
            ocrOverlay.setPreviewSize(imageProxy.getWidth(), imageProxy.getHeight());
        }

        textRecognizer.process(image)
                .addOnSuccessListener(text -> {
                    lastAnalysisTime = System.currentTimeMillis();
                    updateUiWithText(text);
                })
                .addOnFailureListener(e -> Log.e(TAG, "OCR failed", e))
                .addOnCompleteListener(task -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    private void updateUiWithText(Text text) {
        String fullText = text.getText().trim();
        runOnUiThread(() -> {
            if (isFrozen) return; // Prevent late callbacks

            if (fullText.isEmpty()) {
                lastCapturedTextObj = null;
                tvStatus.setText(getString(R.string.msg_point_camera));
                tvTip.setVisibility(View.VISIBLE);
                ocrOverlay.setItems(java.util.Collections.emptyList());
                btnCapture.setEnabled(false);
                btnCapture.setAlpha(0.5f);
            } else {
                lastCapturedTextObj = text;
                tvStatus.setText(getString(R.string.msg_text_detected));
                tvTip.setVisibility(View.GONE);
                btnCapture.setEnabled(true);
                btnCapture.setAlpha(1.0f);

                // Update Overlays
                List<OverlayItem> patternItems = ArPatternHighlighter.detectPatterns(this, text);
                ocrOverlay.setItems(patternItems);
            }
        });
    }

    private void enterFrozenMode() {
        if (lastCapturedTextObj == null) return;
        
        // Capture bitmap from preview to freeze it visually
        android.graphics.Bitmap snapshot = previewView.getBitmap();
        if (snapshot != null) {
            frozenImageView.setImageBitmap(snapshot);
            frozenImageView.setVisibility(View.VISIBLE);
        }
        
        isFrozen = true;
        ocrOverlay.setFrozen(true);
        selectionDimOverlay.setVisibility(View.VISIBLE);
        
        btnCapture.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);
        guideFrame.setVisibility(View.GONE); // Hide guide frame in frozen mode for cleaner selection
        
        tvStatus.setVisibility(View.GONE); // Hide guides in frozen mode
        tvTip.setVisibility(View.GONE);
        
        // Pass text to overlay then extract all automatically
        ocrOverlay.setTextBlocks(lastCapturedTextObj.getTextBlocks());
        ocrOverlay.clearSelection();
        
        String extracted = ocrOverlay.getAllExtractedText();
        tvDetectedText.setText(extracted);
        btnAnalyze.setEnabled(!extracted.isEmpty());
        btnCopy.setEnabled(!extracted.isEmpty());
        
        if (extracted.isEmpty()) {
             tvDetectedText.setText(getString(R.string.msg_no_text_found));
        }
    }

    private void exitFrozenMode() {
        isFrozen = false;
        ocrOverlay.setFrozen(false);
        selectionDimOverlay.setVisibility(View.GONE);
        frozenImageView.setVisibility(View.GONE);
        frozenImageView.setImageBitmap(null);
        
        btnCapture.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.GONE);
        guideFrame.setVisibility(View.VISIBLE);
        guideFrame.setAlpha(1.0f);
        
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.msg_point_camera));
        tvTip.setVisibility(View.VISIBLE);
        tvTip.setText(getString(R.string.tip_align_frame));
        
        ocrOverlay.clearSelection();
        ocrOverlay.setTextBlocks(new java.util.ArrayList<>());
        
        // Reset as plain text without any spans/links
        tvDetectedText.setAutoLinkMask(0);
        tvDetectedText.setText(getString(R.string.msg_waiting_detection));
        
        btnAnalyze.setEnabled(false);
        btnCopy.setEnabled(false);
        floatingBubble.setVisibility(View.GONE);
    }

    private void copySelectedText() {
        String textToCopy = tvDetectedText.getText().toString();
        if (!textToCopy.isEmpty() && !textToCopy.equals(getString(R.string.msg_waiting_detection))) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Scanned Text", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.msg_copied_clipboard), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSenderConfirmationDialog() {
        String textToAnalyze = tvDetectedText.getText().toString();
        if (textToAnalyze.isEmpty() || textToAnalyze.equals(getString(R.string.msg_waiting_detection))) return;

        CameraScanTextParser.ParsedScanResult parsed = CameraScanTextParser.parse(this, textToAnalyze);
        String detectedSender = parsed.sender != null ? parsed.sender : "";

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_sender, null);
        android.widget.EditText etSender = dialogView.findViewById(R.id.etSenderName);
        etSender.setText(detectedSender);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_confirm_sender))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.btn_continue), null)
                .setNegativeButton(getString(R.string.btn_back_custom), (d, which) -> d.dismiss())
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnContinue = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btnContinue.setOnClickListener(v -> {
                String editedSender = etSender.getText().toString().trim();
                if (editedSender.isEmpty()) {
                    Toast.makeText(ArScanActivity.this, getString(R.string.msg_confirm_sender_name), Toast.LENGTH_SHORT).show();
                } else {
                    dialog.dismiss();
                    parsed.sender = editedSender;
                    navigateToResultWithSender(textToAnalyze, parsed);
                }
            });
        });
        dialog.show();
    }

    private void navigateToResultWithSender(String textToAnalyze, CameraScanTextParser.ParsedScanResult parsed) {
        Intent intent = new Intent(this, ResultActivity.class);
        
        ScanResult arResult = new ScanResult();
        // Use currently visible (and potentially edited) text
        arResult.setSnippet(textToAnalyze);
        arResult.setTimestamp(System.currentTimeMillis());
        arResult.setLabel("PENDING");
        arResult.setConfidence(0);
        
        if (!parsed.detectedLinks.isEmpty()) {
            arResult.setUrls(parsed.detectedLinks);
        }

        intent.putExtra("scan_result", arResult);
        intent.putExtra("sender", parsed.sender);
        intent.putExtra("source", "ar_scan");
        intent.putExtra("raw_full_text", textToAnalyze);
        navigateWithLoading(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
