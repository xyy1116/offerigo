package com.chuan.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeUrlValidatorTest {

    @Test
    void rejectsLocalAndPrivateAddresses() {
        assertFalse(SafeUrlValidator.isSafeHttpUrl("http://localhost:8080/job"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl("http://127.0.0.1/job"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl("http://192.168.1.10/job"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl("http://10.0.0.5/job"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl("http://169.254.169.254/latest/meta-data"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl("ftp://example.com/file"));
        assertFalse(SafeUrlValidator.isSafeHttpUrl(""));
    }

    @Test
    void acceptsPublicHttpAddress() {
        assertTrue(SafeUrlValidator.isSafeHttpUrl("https://8.8.8.8/job"));
    }
}