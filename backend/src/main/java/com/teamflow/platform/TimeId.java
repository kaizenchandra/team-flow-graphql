package com.teamflow.platform;

/**
 * Timestamp-prefixed UUID-shaped identifier; random suffix breaks ties within a millisecond.
 */
public final class TimeId {

    private TimeId() {
    }

    public static String next() {
        String time = String.format("%012x", System.currentTimeMillis());
        String random = java.util.UUID.randomUUID().toString();
        return (
                time.substring(0, 8) + "-" + time.substring(8) + random.substring(13)
        );
    }
}
