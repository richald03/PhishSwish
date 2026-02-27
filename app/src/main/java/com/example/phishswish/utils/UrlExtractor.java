package com.example.phishswish.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://\\S+)|(www\\.\\S+)",
            Pattern.CASE_INSENSITIVE
    );

    public static String extractFirstUrl(String text) {
        if (text == null) return null;

        Matcher matcher = URL_PATTERN.matcher(text);

        if (matcher.find()) {
            String url = matcher.group().trim();

            if (url.startsWith("www.")) {
                url = "https://" + url;
            }

            url = url.replaceAll("[),.]+$", "");
            return url;
        }

        return null;
    }
}
