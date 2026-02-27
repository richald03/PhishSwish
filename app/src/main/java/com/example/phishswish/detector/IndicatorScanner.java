package com.example.phishswish.detector;

import com.example.phishswish.model.ScanIndicators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndicatorScanner {

    // Keyword lists
    private static final List<String> OTP_KEYWORDS = Arrays.asList(
            "otp", "one-time password", "verification code", "verify your account"
    );

    private static final List<String> URGENCY_KEYWORDS = Arrays.asList(
            "urgent", "immediately", "action required", "expires soon", "suspended", "locked", "unauthorized access"
    );

    private static final List<String> MONEY_PRIZE_KEYWORDS = Arrays.asList(
            "winner", "prize", "congratulations", "cash", "reward", "lottery", "claim now", "refund"
    );

    private static final List<String> BRAND_WORDS_PH = Arrays.asList(
            "gcash", "maya", "bdo", "bpi", "landbank", "shopee", "lazada", "globe", "smart"
    );

    private static final List<String> PERSONAL_INFO_KEYWORDS = Arrays.asList(
            "update password", "confirm identity", "security details", "credit card", "bank account", "ssn", "tin"
    );

    private static final List<String> SHORT_LINK_DOMAINS = Arrays.asList(
            "bit.ly", "tinyurl.com", "goo.gl", "t.co", "is.gd", "buff.ly", "rebrand.ly", "ow.ly"
    );

    // URL Extraction Regex
    private static final String URL_REGEX = "((http|https)://|(www)\\.)[a-z0-9\\-]+(\\.[a-z0-9\\-]+)+([/?].*)?";

    public ScanIndicators scan(String text) {
        ScanIndicators results = new ScanIndicators();
        if (text == null || text.isEmpty()) return results;

        String lowerText = text.toLowerCase();

        // Check for keywords and add indicators
        checkKeywords(lowerText, OTP_KEYWORDS, "OTP Request", results);
        checkKeywords(lowerText, URGENCY_KEYWORDS, "Urgency/Threat", results);
        checkKeywords(lowerText, MONEY_PRIZE_KEYWORDS, "Money or Prize Hook", results);
        checkKeywords(lowerText, BRAND_WORDS_PH, "Brand Impersonation (PH)", results);
        checkKeywords(lowerText, PERSONAL_INFO_KEYWORDS, "Personal Info Request", results);

        // Extract URLs
        List<String> urls = extractUrls(text);
        results.setUrls(urls);

        // Check for shortened links
        for (String url : urls) {
            for (String shortDomain : SHORT_LINK_DOMAINS) {
                if (url.toLowerCase().contains(shortDomain)) {
                    results.setHasShortenedUrl(true);
                    results.addIndicator("Shortened URL Detected");
                    break;
                }
            }
        }

        return results;
    }

    private void checkKeywords(String text, List<String> keywords, String indicatorName, ScanIndicators results) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                results.addIndicator(indicatorName);
                break;
            }
        }
    }

    private List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        Pattern pattern = Pattern.compile(URL_REGEX, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }
}
