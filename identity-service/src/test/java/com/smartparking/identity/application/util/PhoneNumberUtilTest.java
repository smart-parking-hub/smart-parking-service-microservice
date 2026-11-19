package com.smartparking.identity.application.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberUtilTest {

    @Test
    @DisplayName("Normalizes various valid phone formats with spaces, +84, hyphens")
    void testNormalizePhone() {
        assertEquals("0912345678", PhoneNumberUtil.normalizePhone("0912 345 678"));
        assertEquals("0912345678", PhoneNumberUtil.normalizePhone("+84 912 345 678"));
        assertEquals("0912345678", PhoneNumberUtil.normalizePhone("+84912345678"));
        assertEquals("0912345678", PhoneNumberUtil.normalizePhone("84912345678"));
        assertEquals("0988888888", PhoneNumberUtil.normalizePhone(" 0988-888-888 "));
        assertEquals("0988888888", PhoneNumberUtil.normalizePhone("0988.888.888"));
        assertEquals("0988888888", PhoneNumberUtil.normalizePhone("(0988) 888 888"));
    }

    @Test
    @DisplayName("Handles null and empty string safely")
    void testNullAndEmpty() {
        assertNull(PhoneNumberUtil.normalizePhone(null));
        assertEquals("", PhoneNumberUtil.normalizePhone(""));
        assertEquals("", PhoneNumberUtil.normalizePhone("   "));
    }
}
