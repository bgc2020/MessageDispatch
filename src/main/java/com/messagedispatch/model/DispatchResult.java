package com.messagedispatch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message Dispatch Result
 * 
 * Contains the result of a message dispatch operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchResult {

    /**
     * Whether the dispatch was successful
     */
    private boolean success;

    /**
     * Original message ID
     */
    private String messageId;

    /**
     * Number of recipients
     */
    private int recipientCount;

    /**
     * Number of successfully delivered messages
     */
    private int deliveredCount;

    /**
     * Number of failed deliveries
     */
    private int failedCount;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;

    /**
     * Dispatch timestamp
     */
    private long timestamp;

    /**
     * Create a successful result
     */
    public static DispatchResult success(String messageId, int recipientCount, int deliveredCount, long processingTimeMs) {
        return DispatchResult.builder()
                .success(true)
                .messageId(messageId)
                .recipientCount(recipientCount)
                .deliveredCount(deliveredCount)
                .failedCount(recipientCount - deliveredCount)
                .processingTimeMs(processingTimeMs)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a failed result
     */
    public static DispatchResult failure(String messageId, String errorMessage) {
        return DispatchResult.builder()
                .success(false)
                .messageId(messageId)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
