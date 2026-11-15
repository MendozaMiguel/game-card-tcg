package com.ordredesarcane.online.security;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class XssSanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private static final int DEFAULT_MAX_LENGTH = 500;

    public String sanitize(String input) {
        return sanitize(input, DEFAULT_MAX_LENGTH);
    }

    public String sanitize(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String cleaned = CONTROL_CHARS.matcher(input).replaceAll("").trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return HtmlUtils.htmlEscape(cleaned, "UTF-8");
    }
}
