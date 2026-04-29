package com.boardexam.util;

import java.security.SecureRandom;

/**
 * Generates CBSE-style masked IDs for answer scripts.
 *
 * Format: BX-<YEAR>-<6-CHAR-ALPHANUMERIC>
 * Example: BX-2026-A3F9K2
 *
 * Uses SecureRandom (CSPRNG) — not Math.random().
 * This ensures unpredictability, preventing evaluators from
 * guessing which script belongs to which center/student.
 */
public final class MaskIdGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // Note: No I, O, 0, 1 to avoid visual confusion
    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_LENGTH = 6;

    private MaskIdGenerator() {}

    public static String generate(int year) {
        StringBuilder sb = new StringBuilder("BX-").append(year).append("-");
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public static String generate() {
        return generate(java.time.Year.now().getValue());
    }
}
