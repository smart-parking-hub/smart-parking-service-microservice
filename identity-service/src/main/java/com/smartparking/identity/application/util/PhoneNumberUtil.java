package com.smartparking.identity.application.util;

public final class PhoneNumberUtil {

    private PhoneNumberUtil() {}

    /**
     * Normalizes a Vietnamese phone number:
     * - Removes all whitespace, hyphens, dots, parentheses
     * - Converts leading "+84" or "84" (when length is 11) to "0"
     * - Returns null if raw phone is null
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String cleaned = rawPhone.replaceAll("[\\s\\-\\.\\(\\)]", "").trim();
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        return cleaned;
    }
}
