package com.messagedispatch.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for time-related operations
 */
public final class TimeUtils {

    private static final DateTimeFormatter ISO_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneId.of("UTC"));

    private TimeUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get current timestamp in ISO 8601 format
     */
    public static String nowISO() {
        return ISO_FORMATTER.format(Instant.now());
    }

    /**
     * Format Instant to ISO 8601 string
     */
    public static String formatISO(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ISO_FORMATTER.format(instant);
    }

    /**
     * Parse ISO 8601 string to Instant
     */
    public static Instant parseISO(String isoString) {
        if (isoString == null || isoString.isEmpty()) {
            return null;
        }
        return Instant.parse(isoString);
    }

    /**
     * Get current timestamp in milliseconds
     */
    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Calculate duration between two timestamps
     */
    public static long durationMs(long startTime, long endTime) {
        return endTime - startTime;
    }

    /**
     * Check if a timestamp is expired
     */
    public static boolean isExpired(Instant expiresAt) {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
