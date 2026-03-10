package com.example.phishswish.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OcrOverlayView extends View {

    // Pattern overlay items (suspicious URL, OTP, urgency, brand)
    private final List<OverlayItem> patternItems = new ArrayList<>();

    // Scanner focus area
    private Rect scannerRect = null;

    // Selectable text items (word/element level)
    private final List<SelectableOcrItem> selectableItems = new ArrayList<>();
    private int selectionStartIdx = -1;
    private int selectionEndIdx = -1;
    private boolean isFrozen = false;
    private boolean isDraggingStartHandle = false;
    private boolean isDraggingEndHandle = false;
    private boolean isDraggingNewSelection = false;

    public static class SelectableOcrItem {
        public final Rect rect;
        public final String text;
        public final int lineIndex;

        public SelectableOcrItem(Rect rect, String text, int lineIndex) {
            this.rect = rect;
            this.text = text;
            this.lineIndex = lineIndex;
        }
    }

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint textBgPaint = new Paint();
    private final Paint selectableBoxPaint = new Paint();
    private final Paint selectedBoxPaint = new Paint();
    private final Paint selectedFillPaint = new Paint();
    private final Paint handlePaint = new Paint();
    private final Paint lightSelectionFillPaint = new Paint();
    private final Paint lightSelectionBoxPaint = new Paint();

    private int previewWidth = 0;
    private int previewHeight = 0;

    private OnBlockSelectedListener blockSelectedListener;

    public interface OnBlockSelectedListener {
        void onBlockSelectionChanged(String selectedText);
        void onSelectionPositionChanged(RectF selectionBounds);
    }

    public OcrOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Pattern overlay paints
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        textBgPaint.setStyle(Paint.Style.FILL);
        textBgPaint.setAntiAlias(true);

        // Selectable block outline (subtle white dashes)
        selectableBoxPaint.setStyle(Paint.Style.STROKE);
        selectableBoxPaint.setStrokeWidth(2f);
        selectableBoxPaint.setColor(Color.parseColor("#80FFFFFF"));
        selectableBoxPaint.setAntiAlias(true);

        // Selected block outline (solid blue)
        selectedBoxPaint.setStyle(Paint.Style.STROKE);
        selectedBoxPaint.setStrokeWidth(4f);
        selectedBoxPaint.setColor(Color.parseColor("#3B82F6"));
        selectedBoxPaint.setAntiAlias(true);

        // Selected block fill (translucent blue)
        selectedFillPaint.setStyle(Paint.Style.FILL);
        selectedFillPaint.setColor(Color.parseColor("#333B82F6"));
        selectedFillPaint.setAntiAlias(true);

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.parseColor("#3B82F6"));
        handlePaint.setAntiAlias(true);

        // Light selection preview for frozen mode (all detected words)
        lightSelectionFillPaint.setStyle(Paint.Style.FILL);
        lightSelectionFillPaint.setColor(Color.parseColor("#223B82F6")); // ~13% blue opacity
        lightSelectionFillPaint.setAntiAlias(true);

        lightSelectionBoxPaint.setStyle(Paint.Style.STROKE);
        lightSelectionBoxPaint.setStrokeWidth(2f);
        lightSelectionBoxPaint.setColor(Color.parseColor("#4D3B82F6")); // ~30% blue opacity
        lightSelectionBoxPaint.setAntiAlias(true);
    }

    public void setOnBlockSelectedListener(OnBlockSelectedListener listener) {
        this.blockSelectedListener = listener;
    }

    public void setPreviewSize(int width, int height) {
        this.previewWidth = width;
        this.previewHeight = height;
    }

    public void setScannerRect(Rect rect) {
        this.scannerRect = rect;
        postInvalidate();
    }

    /**
     * Set pattern overlay items (suspicious URL, OTP, urgency, brand).
     * These are display-only overlays.
     */
    public void setItems(List<OverlayItem> newItems) {
        patternItems.clear();
        if (newItems != null) {
            for (OverlayItem item : newItems) {
                if (item.rect != null && isInsideScanner(item.rect)) {
                    patternItems.add(item);
                }
            }
        }
        postInvalidate();
    }

    /**
     * Set selectable text blocks from OCR. Clears any previous selection if significant scene change.
     */
    public void setTextBlocks(List<Text.TextBlock> blocks) {
        int oldCount = selectableItems.size();
        selectableItems.clear();
        
        if (blocks != null) {
            // Group down to elements
            List<Text.Element> allElements = new ArrayList<>();
            for (Text.TextBlock block : blocks) {
                for (Text.Line line : block.getLines()) {
                    for (Text.Element element : line.getElements()) {
                        if (element.getBoundingBox() != null && isInsideScanner(element.getBoundingBox())) {
                             allElements.add(element);
                        }
                    }
                }
            }

            // Word Grouping algorithm:
            // 1. Sort by Y position
            java.util.Collections.sort(allElements, (a, b) -> Integer.compare(a.getBoundingBox().top, b.getBoundingBox().top));

            // 2. Group into lines
            List<List<Text.Element>> elementLines = new ArrayList<>();
            List<Text.Element> currentLine = new ArrayList<>();
            int currentYThreshold = -1;

            // Threshold for same line depends on text size, let's use a dynamic one or safe fixed one ~15px
            for (Text.Element e : allElements) {
                if (currentLine.isEmpty()) {
                    currentLine.add(e);
                    currentYThreshold = e.getBoundingBox().bottom;
                } else {
                    int verticalOverlap = Math.min(currentYThreshold, e.getBoundingBox().bottom) - Math.max(currentLine.get(0).getBoundingBox().top, e.getBoundingBox().top);
                    if (verticalOverlap > 0 || Math.abs(e.getBoundingBox().top - currentLine.get(0).getBoundingBox().top) < e.getBoundingBox().height() / 2) {
                        currentLine.add(e);
                        currentYThreshold = Math.max(currentYThreshold, e.getBoundingBox().bottom);
                    } else {
                        elementLines.add(new ArrayList<>(currentLine));
                        currentLine.clear();
                        currentLine.add(e);
                        currentYThreshold = e.getBoundingBox().bottom;
                    }
                }
            }
            if (!currentLine.isEmpty()) {
                elementLines.add(currentLine);
            }

            // 3. Sort each line by X coordinate
            int lineIdx = 0;
            for (List<Text.Element> lineElements : elementLines) {
                java.util.Collections.sort(lineElements, (a, b) -> Integer.compare(a.getBoundingBox().left, b.getBoundingBox().left));
                for (Text.Element e : lineElements) {
                    selectableItems.add(new SelectableOcrItem(e.getBoundingBox(), e.getText(), lineIdx));
                }
                lineIdx++;
            }
        }
        
        // Clear selection only if a fresh scan comes in directly without user lock, or block count swings too much
        if (Math.abs((blocks != null ? blocks.size() : 0) - oldCount) > 5) {
            selectionStartIdx = -1;
            selectionEndIdx = -1;
        }
        postInvalidate();
    }

    public void setFrozen(boolean frozen) {
        this.isFrozen = frozen;
        postInvalidate();
    }

    /**
     * Clear the current block selection.
     */
    public void clearSelection() {
        selectionStartIdx = -1;
        selectionEndIdx = -1;
        postInvalidate();
        if (blockSelectedListener != null) {
            blockSelectedListener.onBlockSelectionChanged("");
        }
    }

    /**
     * Returns the combined text of all currently selected blocks.
     */
    /**
     * Returns the combined text of all extracted blocks (everything inside scanner box).
     */
    public String getAllExtractedText() {
        if (selectableItems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        SelectableOcrItem lastItem = null;

        for (SelectableOcrItem item : selectableItems) {
            if (lastItem != null) {
                if (item.lineIndex != lastItem.lineIndex) {
                    sb.append("\n");
                } else {
                    if (!shouldJoinWithoutSpace(lastItem, item)) {
                        sb.append(" ");
                    }
                }
            }
            sb.append(item.text);
            lastItem = item;
        }
        return sb.toString().trim();
    }

    /**
     * Returns the combined text of only currently selected blocks (optional usage).
     */
    public String getSelectedText() {
        if (selectionStartIdx == -1 || selectionEndIdx == -1) return getAllExtractedText();
        int start = Math.min(selectionStartIdx, selectionEndIdx);
        int end = Math.max(selectionStartIdx, selectionEndIdx);
        
        StringBuilder sb = new StringBuilder();
        SelectableOcrItem lastItem = null;

        for (int i = start; i <= end; i++) {
            if (i >= 0 && i < selectableItems.size()) {
                SelectableOcrItem item = selectableItems.get(i);
                if (lastItem != null) {
                    if (item.lineIndex != lastItem.lineIndex) {
                        sb.append("\n");
                    } else {
                        if (!shouldJoinWithoutSpace(lastItem, item)) {
                            sb.append(" ");
                        }
                    }
                }
                sb.append(item.text);
                lastItem = item;
            }
        }
        return sb.toString().trim();
    }

    private boolean isInsideScanner(Rect imageRect) {
        if (scannerRect == null || previewWidth == 0 || previewHeight == 0) return true;
        RectF mapped = getMappedRect(imageRect);
        float centerX = mapped.centerX();
        float centerY = mapped.centerY();
        return scannerRect.contains((int) centerX, (int) centerY);
    }

    private boolean shouldJoinWithoutSpace(SelectableOcrItem prev, SelectableOcrItem curr) {
        if (prev.lineIndex != curr.lineIndex) return false;
        
        String p = prev.text;
        String c = curr.text;
        
        // Check for URL-like punctuation that implies joining
        if (p.endsWith(":") || p.endsWith("/") || p.endsWith(".") || p.endsWith("@") || p.endsWith("?") || p.endsWith("&") || p.endsWith("=")) return true;
        if (c.startsWith(".") || c.startsWith("/") || c.startsWith("?") || c.startsWith("&") || c.startsWith("=") || c.startsWith("#")) return true;

        // Visual proximity check: if words are VERY close, they likely form one token (like a split URL)
        int gap = curr.rect.left - prev.rect.right;
        // Tighter gap for link/word joining: ~25% of height
        int gapThreshold = Math.min(prev.rect.height(), curr.rect.height()) / 4;
        
        return gap <= gapThreshold;
    }

    /**
     * Returns the bounding box of the current selection in screen pixels.
     */
    public RectF getSelectionBounds() {
        if (selectionStartIdx == -1 || selectionEndIdx == -1 || previewWidth == 0 || previewHeight == 0) return null;

        int start = Math.min(selectionStartIdx, selectionEndIdx);
        int end = Math.max(selectionStartIdx, selectionEndIdx);

        RectF selectionRect = null;
        for (int i = start; i <= end; i++) {
            Rect bounds = selectableItems.get(i).rect;
            RectF mapped = getMappedRect(bounds);
            if (selectionRect == null) {
                selectionRect = new RectF(mapped);
            } else {
                selectionRect.union(mapped);
            }
        }
        return selectionRect;
    }

    private RectF getMappedRect(Rect bounds) {
        if (previewWidth == 0 || previewHeight == 0) return new RectF(bounds);
        
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        
        // Exact mapping matching CameraX FILL_CENTER scaling
        float scale = Math.max(viewWidth / previewWidth, viewHeight / previewHeight);
        
        float scaledImageWidth = previewWidth * scale;
        float scaledImageHeight = previewHeight * scale;
        
        float dx = (viewWidth - scaledImageWidth) / 2f;
        float dy = (viewHeight - scaledImageHeight) / 2f;

        return new RectF(
                bounds.left * scale + dx,
                bounds.top * scale + dy,
                bounds.right * scale + dx,
                bounds.bottom * scale + dy
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }
        
        if (previewWidth == 0 || previewHeight == 0) return super.onTouchEvent(event);
        if (selectableItems.isEmpty()) return super.onTouchEvent(event);

        float touchX = event.getX();
        float touchY = event.getY();

        boolean changed = false;

        if (action == MotionEvent.ACTION_DOWN) {
            isDraggingStartHandle = false;
            isDraggingEndHandle = false;
            isDraggingNewSelection = false;

            // Check if touched a handle
            if (selectionStartIdx != -1 && selectionEndIdx != -1) {
                int start = Math.min(selectionStartIdx, selectionEndIdx);
                int end = Math.max(selectionStartIdx, selectionEndIdx);

                RectF startRect = getMappedRect(selectableItems.get(start).rect);
                RectF endRect = getMappedRect(selectableItems.get(end).rect);

                float handleRadius = 50f;
                // Start handle is top-left
                if (Math.hypot(touchX - startRect.left, touchY - startRect.top) < handleRadius * 2) {
                    isDraggingStartHandle = true;
                    return true;
                }
                // End handle is bottom-right
                if (Math.hypot(touchX - endRect.right, touchY - endRect.bottom) < handleRadius * 2) {
                    isDraggingEndHandle = true;
                    return true;
                }
            }

            // Check if touched a word (with tighter tapping tolerance)
            for (int i = 0; i < selectableItems.size(); i++) {
                RectF mapped = getMappedRect(selectableItems.get(i).rect);
                // Smaller inset for tighter selection focus
                mapped.inset(-12, -12);
                if (mapped.contains(touchX, touchY)) {
                    isDraggingNewSelection = true;
                    selectionStartIdx = i;
                    selectionEndIdx = i;
                    changed = true;
                    break;
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (isDraggingStartHandle || isDraggingEndHandle || isDraggingNewSelection) {
                // Find nearest word with a strict tolerance threshold
                int nearestIdx = -1;
                float minDistance = Float.MAX_VALUE;
                for (int i = 0; i < selectableItems.size(); i++) {
                    RectF mapped = getMappedRect(selectableItems.get(i).rect);
                    // Finding nearest word with a strict tolerance threshold
                    RectF expandedMapped = new RectF(mapped);
                    expandedMapped.inset(-25, -25); // Tighter snapping for better precision
                    
                    if (expandedMapped.contains(touchX, touchY)) {
                        float centerX = mapped.centerX();
                        float centerY = mapped.centerY();
                        float dist = (float) Math.hypot(touchX - centerX, touchY - centerY);
                        if (dist < minDistance) {
                            minDistance = dist;
                            nearestIdx = i;
                        }
                    }
                }

                if (nearestIdx != -1) {
                    if (isDraggingStartHandle) {
                        if (selectionStartIdx != nearestIdx) {
                            selectionStartIdx = nearestIdx;
                            changed = true;
                        }
                    } else if (isDraggingEndHandle) {
                        if (selectionEndIdx != nearestIdx) {
                            selectionEndIdx = nearestIdx;
                            changed = true;
                        }
                    } else if (isDraggingNewSelection) {
                        if (selectionEndIdx != nearestIdx) {
                            selectionEndIdx = nearestIdx;
                            changed = true;
                        }
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isDraggingStartHandle = false;
            isDraggingEndHandle = false;
            isDraggingNewSelection = false;
        }

        if (changed) {
            postInvalidate();
            if (blockSelectedListener != null) {
                blockSelectedListener.onBlockSelectionChanged(getSelectedText());
                blockSelectedListener.onSelectionPositionChanged(getSelectionBounds());
            }
        }

        // Return true if we consumed drag, else let touches fall through
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (previewWidth == 0 || previewHeight == 0) return;

        // 1. Draw selectable text block outlines (Disabled in frozen mode for cleaner UI)
        if (!isFrozen) {
            int startSelection = -1;
            int endSelection = -1;
            if (selectionStartIdx != -1 && selectionEndIdx != -1) {
                startSelection = Math.min(selectionStartIdx, selectionEndIdx);
                endSelection = Math.max(selectionStartIdx, selectionEndIdx);
            }

            for (int i = 0; i < selectableItems.size(); i++) {
                Rect bounds = selectableItems.get(i).rect;
                RectF mapped = getMappedRect(bounds);

                if (i >= startSelection && i <= endSelection) {
                    // Draw selected state: blue fill with small rounded padding
                    RectF highlight = new RectF(mapped);
                    highlight.inset(-4, -4); 
                    canvas.drawRoundRect(highlight, 12f, 12f, selectedFillPaint);
                }
            }

            // Draw selection handles only if dragging
            if ((isDraggingStartHandle || isDraggingEndHandle) && startSelection != -1 && endSelection != -1) {
                RectF startRect = getMappedRect(selectableItems.get(startSelection).rect);
                RectF endRect = getMappedRect(selectableItems.get(endSelection).rect);
                
                float handleRadius = 24f;
                canvas.drawCircle(startRect.left, startRect.top, handleRadius, handlePaint);
                canvas.drawCircle(endRect.right, endRect.bottom, handleRadius, handlePaint);
            }
        }

        // 2. Draw pattern overlay items on top ONLY in live mode
        if (!isFrozen) {
            for (OverlayItem item : patternItems) {
                if (item.rect == null) continue;

                RectF mappedRect = getMappedRect(item.rect);

                int color;
                switch (item.type) {
                    case SUSPICIOUS_URL: color = Color.RED; break;
                    case OTP_REQUEST: color = Color.parseColor("#FB8C00"); break;
                    case URGENCY_TACTIC: color = Color.YELLOW; break;
                    case BRAND_MENTION: color = Color.CYAN; break;
                    case LIKELY_SAFE: color = Color.parseColor("#16A34A"); break;
                    default: color = Color.WHITE;
                }

                boxPaint.setColor(color);
                textBgPaint.setColor(color);
                textPaint.setAlpha(255);

                canvas.drawRoundRect(mappedRect, 8f, 8f, boxPaint);

                // Draw label
                String label = item.label;
                float textWidth = textPaint.measureText(label);
                Rect textBounds = new Rect();
                textPaint.getTextBounds(label, 0, label.length(), textBounds);
                float textHeight = textBounds.height();

                RectF labelBg = new RectF(
                        mappedRect.left,
                        mappedRect.top - textHeight - 20,
                        mappedRect.left + textWidth + 20,
                        mappedRect.top
                );
                canvas.drawRoundRect(labelBg, 4f, 4f, textBgPaint);
                canvas.drawText(label, mappedRect.left + 10, mappedRect.top - 12, textPaint);
            }
        }
    }
}
