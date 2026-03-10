package com.example.phishswish.utils;

import android.content.Context;
import com.example.phishswish.R;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for identifying and normalizing message senders.
 */
public class SenderDetector {

    public static final String UNKNOWN = "Unknown"; // Keeping for reference but should use R.string
    
    private static final String[] BRAND_KEYWORDS = {
            "gcash", "maya", "bdo", "bpi", "shopee", "lazada", "paypal", "bank", "support", "security"
    };

    // Email regex for sender detection in Camera Scanning
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
    );

    /**
     * Determines the sender from message text (OCR/AR or Paste Scan).
     *
     * @param context The context for localized strings.
     * @param text The full message content.
     * @return The identified sender name or localized "Unknown".
     */
    public static String detectFromText(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            return context.getString(R.string.sender_unknown);
        }

        String trimmed = text.trim();
        String lowercase = trimmed.toLowerCase(Locale.ROOT);

        // Strategy 3: Paste Scan - Extract word before ":"
        // Example: "GCash: Your OTP is..." -> "GCash"
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0 && colonIndex < 30) {
            String potentialSender = trimmed.substring(0, colonIndex).trim();
            if (!potentialSender.isEmpty() && !potentialSender.contains("\n")) {
                return potentialSender;
            }
        }

        // Strategy 2: OCR / AR Scan - Detect brand keywords near the beginning
        // Use first 50 characters as "near the beginning"
        String searchArea = lowercase.substring(0, Math.min(lowercase.length(), 50));
        for (String brand : BRAND_KEYWORDS) {
            if (searchArea.contains(brand)) {
                return formatBrandName(brand);
            }
        }

        return context.getString(R.string.sender_unknown);
    }

    /**
     * Normalizes a sender string (e.g. from SMS Inbox or user input).
     *
     * @param context The context for localized strings.
     * @param sender The raw sender string.
     * @return Trimmed sender or localized "Unknown" if invalid.
     */
    public static String normalize(Context context, String sender) {
        if (sender == null || sender.trim().isEmpty() || 
            sender.equalsIgnoreCase("Unknown") || 
            sender.equalsIgnoreCase("Hindi Kilala")) {
            return context.getString(R.string.sender_unknown);
        }
        return sender.trim();
    }

    /**
     * Intelligent sender detection specifically for Camera Scanning (OCR).
     */
    public static String detectForCameraScan(Context context, String text) {
        String unknown = context != null ? context.getString(R.string.sender_unknown) : UNKNOWN;

        if (text == null || text.trim().isEmpty()) {
            return unknown;
        }

        // Normalization
        String normalized = text.trim().replaceAll(" +", " ").replaceAll("\\\\r\\\\n?", "\n");
        String lowercase = normalized.toLowerCase(Locale.ROOT);
        
        // Read only first 1-2 lines
        String[] lines = normalized.split("\n");
        String firstLine = lines.length > 0 ? lines[0].trim() : "";
        String secondLine = lines.length > 1 ? lines[1].trim() : "";
        String topText = (firstLine + " " + secondLine).trim().toLowerCase(Locale.ROOT);
        
        String sender = null;

        // 1. Explicit Sender Pattern (e.g. "GCash: Your OTP is...")
        int colonIndex = normalized.indexOf(':');
        if (colonIndex > 0 && colonIndex <= 20) {
            String prefix = normalized.substring(0, colonIndex).trim();
            if (!prefix.contains("http") && isStrictlyValidSender(prefix)) {
                sender = prefix;
            }
        }

        // 1b. Explicit "From:" email pattern (e.g. "From: support@gmail.com")
        if (sender == null) {
            sender = detectFromPrefixEmail(normalized);
        }

        // 2. Known Brand / Company Dictionary
        if (sender == null) {
            sender = matchKnownBrandOrCompany(topText);
        }

        // 3. Valid email address found in the OCR text
        if (sender == null) {
            sender = detectEmailSender(normalized);
        }
        
        // 4. 12-Digit Phone Number on first line
        if (sender == null) {
            String digitsOnly = firstLine.replaceAll("[^0-9]", "");
            if (digitsOnly.length() == 12 && firstLine.replaceAll("[\\s\\-+]", "").matches("^\\+?[0-9]{12}$")) {
                sender = digitsOnly;
            }
        }
        
        // 5. Domain or URL Brand Clue
        if (sender == null) {
            String[] brandDomains = {"gcash.com", "bdo.com.ph", "maya.ph", "shopee.ph", "lazada.com.ph", "paypal.com", "dhl.com", "lbc.com", "netflix.com", "amazon.com", "globe.com.ph", "smart.com.ph"};
            for (String domain : brandDomains) {
                if (lowercase.contains(domain)) {
                    String brand = domain.split("\\.")[0];
                    sender = formatBrandName(brand);
                    break;
                }
            }
        }
        
        // 6. Final strict validation — reject anything that doesn't pass
        if (sender != null && !isStrictlyValidSender(sender)) {
            sender = null;
        }
        
        return sender != null ? sender : unknown;
    }

    // ======================================================================
    // Strict sender validation — only accepts 12-digit numbers, person names,
    // brand names, or company names. Rejects everything else.
    // ======================================================================

    private static boolean isStrictlyValidSender(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return false;
        
        String trimmed = candidate.trim();
        
        // Quick reject: newlines, too long, too short
        if (trimmed.contains("\n")) return false;
        if (trimmed.length() > 40) return false;
        if (trimmed.length() < 2) return false;
        
        // Reject if it looks like a URL
        String low = trimmed.toLowerCase(Locale.ROOT);
        if (low.startsWith("http") || low.startsWith("www.") || low.contains("://")) return false;
        
        // Reject known generic/risky words
        if (isGenericOrRiskyWord(low)) return false;
        
        // (A) 12-digit number
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 12 && trimmed.replaceAll("[\\s\\-+]", "").matches("^\\+?[0-9]{12}$")) {
            return true;
        }
        
        // (B) Known brand/company name
        if (matchKnownBrandOrCompany(low) != null) {
            return true;
        }
        
        // (C) Person name: 1-4 alphabetic words, each 2-20 chars, no risky terms
        if (looksLikePersonName(trimmed)) {
            return true;
        }

        // (D) Valid email address
        if (isValidEmail(trimmed)) {
            return true;
        }
        
        // Nothing matched — reject
        return false;
    }

    private static boolean looksLikePersonName(String s) {
        // Must be mostly alphabetic with spaces/hyphens/periods only
        if (!s.matches("^[A-Za-z][A-Za-z .'-]+$")) return false;
        
        String[] words = s.trim().split("\\s+");
        if (words.length < 1 || words.length > 4) return false;
        
        for (String word : words) {
            String clean = word.replaceAll("[.'-]", "");
            if (clean.length() < 2 || clean.length() > 20) return false;
            // Each word must be alphabetic
            if (!clean.matches("^[A-Za-z]+$")) return false;
        }
        
        // Reject if any word is a generic/risky term
        String low = s.toLowerCase(Locale.ROOT);
        if (isGenericOrRiskyWord(low)) return false;
        for (String word : words) {
            if (isGenericOrRiskyWord(word.toLowerCase(Locale.ROOT))) return false;
        }
        
        return true;
    }

    private static boolean isGenericOrRiskyWord(String low) {
        // Generic message content / risky terms that should never be a sender
        String[] rejectWords = {
            "your", "please", "claim", "verify", "account", "message", "important",
            "security alert", "click here", "click", "here", "otp", "code",
            "verification", "passcode", "urgent", "immediately", "act now",
            "claim now", "suspended", "alert", "warning", "notice", "confirm",
            "update", "expire", "expired", "reset", "login", "password",
            "dear", "prize", "winner", "congratulations", "free", "reward",
            "limited", "offer", "promo", "discount", "subscribe",
            "the", "this", "that", "from", "with", "and", "for"
        };
        for (String word : rejectWords) {
            if (low.equals(word)) return true;
        }
        // Reject multi-word generic phrases
        String[] rejectPhrases = {
            "security alert", "click here", "act now", "claim now",
            "your account", "your otp", "code is", "verification code"
        };
        for (String phrase : rejectPhrases) {
            if (low.contains(phrase)) return true;
        }
        return false;
    }

    /**
     * Matches against a known brand/company dictionary. Returns formatted name or null.
     */
    private static String matchKnownBrandOrCompany(String lowercaseText) {
        // Brands
        String[][] brandMap = {
            {"gcash", "GCash"}, {"maya", "Maya"}, {"bdo", "BDO"}, {"bpi", "BPI"},
            {"shopee", "Shopee"}, {"lazada", "Lazada"}, {"paypal", "PayPal"},
            {"dhl", "DHL"}, {"lbc", "LBC"}, {"j&t", "J&T"},
            {"netflix", "Netflix"}, {"amazon", "Amazon"}, {"grab", "Grab"},
            {"foodpanda", "Foodpanda"}, {"cimb", "CIMB"}, {"rcbc", "RCBC"},
            {"pnb", "PNB"}, {"landbank", "LandBank"}, {"eastwest", "EastWest"},
            {"chinabank", "ChinaBank"}
        };
        // Companies
        String[][] companyMap = {
            {"globe telecom", "Globe Telecom"}, {"smart communications", "Smart Communications"},
            {"security bank", "Security Bank"}, {"union bank", "UnionBank"},
            {"unionbank", "UnionBank"}, {"metrobank", "Metrobank"},
            {"gcash support", "GCash Support"}, {"maya center", "Maya Center"},
            {"globe", "Globe"}, {"smart", "Smart"},
            {"psbank", "PSBank"}, {"aub", "AUB"}
        };
        
        // Check companies first (longer, more specific)
        for (String[] entry : companyMap) {
            if (lowercaseText.contains(entry[0])) {
                return entry[1];
            }
        }
        // Then brands
        for (String[] entry : brandMap) {
            if (lowercaseText.contains(entry[0])) {
                return entry[1];
            }
        }
        return null;
    }

    /**
     * Formats brand keywords for display.
     */
    private static String formatBrandName(String brand) {
        String matched = matchKnownBrandOrCompany(brand.toLowerCase(Locale.ROOT));
        if (matched != null) return matched;
        // Fallback: capitalize first letter
        if (brand.length() > 0) {
            return brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1);
        }
        return brand;
    }

    // ======================================================================
    // Email sender detection helpers (Camera Scanning only)
    // ======================================================================

    /**
     * Detects an email address following a "From:" prefix in OCR text.
     * Example: "From: support@gmail.com" → "support@gmail.com"
     */
    private static String detectFromPrefixEmail(String text) {
        if (text == null) return null;
        // Match "From:" or "from:" followed by an email
        Pattern fromPattern = Pattern.compile(
                "(?i)from\\s*:\\s*([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})"
        );
        Matcher m = fromPattern.matcher(text);
        if (m.find()) {
            String email = m.group(1);
            if (email != null && isValidEmail(email.trim())) {
                return email.trim();
            }
        }
        return null;
    }

    /**
     * Finds the first valid email address in OCR text to use as sender.
     * Skips emails that look like OCR noise.
     */
    private static String detectEmailSender(String text) {
        if (text == null) return null;
        Matcher m = EMAIL_PATTERN.matcher(text);
        while (m.find()) {
            String email = m.group().trim();
            // Strip trailing punctuation that OCR might append
            while (email.endsWith(".") || email.endsWith(",") || email.endsWith(";") || email.endsWith(":")) {
                email = email.substring(0, email.length() - 1);
            }
            if (isValidEmail(email)) {
                return email;
            }
        }
        return null;
    }

    /**
     * Validates that a string is a well-formed email address and not OCR garbage.
     */
    private static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        String trimmed = email.trim();

        // Must contain exactly one @
        int atCount = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '@') atCount++;
        }
        if (atCount != 1) return false;

        // Must not contain spaces
        if (trimmed.contains(" ")) return false;

        // Split into local and domain parts
        String[] parts = trimmed.split("@", 2);
        if (parts.length != 2) return false;

        String local = parts[0];
        String domain = parts[1];

        // Local part must be non-empty and reasonable length
        if (local.isEmpty() || local.length() > 64) return false;

        // Domain must contain at least one dot and a valid TLD (2+ chars)
        if (!domain.contains(".")) return false;
        String[] domainParts = domain.split("\\.");
        if (domainParts.length < 2) return false;
        String tld = domainParts[domainParts.length - 1];
        if (tld.length() < 2 || !tld.matches("^[A-Za-z]+$")) return false;

        // Domain parts must be non-empty and alphanumeric with hyphens
        for (String dp : domainParts) {
            if (dp.isEmpty() || dp.length() > 63) return false;
            if (!dp.matches("^[A-Za-z0-9\\-]+$")) return false;
        }

        // Full match against the email pattern
        return EMAIL_PATTERN.matcher(trimmed).matches();
    }
}
