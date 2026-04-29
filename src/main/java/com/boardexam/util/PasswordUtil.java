package com.boardexam.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Stateless password utility.
 * Uses BCrypt with cost factor 12 (CBSE security baseline).
 * A cost of 12 takes ~300ms to compute — sufficient to resist brute force.
 */
public final class PasswordUtil {

    private static final int BCRYPT_ROUNDS = 12;

    private PasswordUtil() {}

    /** Hash a plaintext password. Store the returned string in the DB. */
    public static String hash(String plaintext) {
        if (plaintext == null || plaintext.length() < 8) {
            throw new IllegalArgumentException(
                "Password must be at least 8 characters.");
        }
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /** Verify a plaintext password against a stored BCrypt hash. */
    public static boolean verify(String plaintext, String hash) {
        if (plaintext == null || hash == null) return false;
        return BCrypt.checkpw(plaintext, hash);
    }

    /** Simple policy check before hashing */
    public static boolean meetsPolicy(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper  = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit  = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial= password.chars().anyMatch(c -> "!@#$%^&*".indexOf(c) >= 0);
        return hasUpper && hasDigit && hasSpecial;
    }
}
