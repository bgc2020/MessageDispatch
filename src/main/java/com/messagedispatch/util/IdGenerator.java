package com.messagedispatch.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Utility class for generating unique identifiers and tokens
 */
public final class IdGenerator {

    private IdGenerator() {
        // Private constructor to prevent instantiation
    }

    /**
     * Generate a unique message ID
     */
    public static String generateMessageId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a unique session ID
     */
    public static String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate a short ID (8 characters)
     */
    public static String generateShortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a hash from input string
     */
    public static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a time-based ID
     */
    public static String generateTimeBasedId() {
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + random;
    }
}
