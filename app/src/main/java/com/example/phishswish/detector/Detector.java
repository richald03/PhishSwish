package com.example.phishswish.detector;

import com.example.phishswish.model.ScanIndicators;
import com.example.phishswish.model.ScanResult;

import java.util.ArrayList;

public class Detector {

    private static final IndicatorScanner scanner = new IndicatorScanner();
    private static final RiskScorer scorer = new RiskScorer();
    private static final ExplanationBuilder explanationBuilder = new ExplanationBuilder();

    /**
     * Analyzes the input text for phishing indicators and returns a complete ScanResult.
     *
     * @param inputText The raw message text to analyze.
     * @return A ScanResult object containing labels, scores, indicators, and explanations.
     */
    public static ScanResult analyze(String inputText) {
        if (inputText == null) {
            inputText = "";
        }
        
        // 1. Normalize text
        String normalizedText = inputText.trim();

        // 2. Scan for indicators (keywords, URLs)
        ScanIndicators indicators = scanner.scan(normalizedText);

        // 3. Score the risk and generate base result
        ScanResult result = scorer.score(indicators, normalizedText);

        // 4. Build detailed explanations and recommendations
        result.setReasons(new ArrayList<>(explanationBuilder.buildReasons(result.getIndicators())));
        result.setRecommendation(explanationBuilder.getRecommendation(result.getLabel()));

        return result;
    }
}
