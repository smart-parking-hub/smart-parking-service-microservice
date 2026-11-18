package com.smartparking.identity.domain.model.verification;

public enum DocumentType {
    BUSINESS_REGISTRATION,
    AUTHORIZATION_LETTER,
    SUPPORTING_DOCUMENT;

    public static boolean isValid(String name) {
        if (name == null) return false;
        try {
            valueOf(name.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
