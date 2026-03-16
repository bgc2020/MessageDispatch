package com.messagedispatch.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Utility class for exception handling
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Get stack trace as string
     */
    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Get root cause of exception
     */
    public static Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Get error message with root cause
     */
    public static String getErrorMessage(Throwable throwable) {
        Throwable rootCause = getRootCause(throwable);
        return rootCause.getMessage();
    }
}
