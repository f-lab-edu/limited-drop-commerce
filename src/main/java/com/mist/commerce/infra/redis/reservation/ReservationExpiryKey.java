package com.mist.commerce.infra.redis.reservation;

public record ReservationExpiryKey (
    Long orderId,
    String rawkey
) {
    private static final String KEY_PREFIX = "reservation:expiry:";

    public ReservationExpiryKey {
        if (orderId == null || rawkey == null || rawkey.isBlank()) {
            throw new IllegalArgumentException("orderId and rawkey must not be null or blank");
        }
    }

    public static ReservationExpiryKey of (Long orderId) {
        return new ReservationExpiryKey(orderId, KEY_PREFIX + orderId);
    }

    public static ReservationExpiryKey from(String rawkey) {
        if (rawkey == null || !rawkey.startsWith(KEY_PREFIX)) {
            return null;
        }

        try {
            Long orderId = Long.valueOf(rawkey.substring(KEY_PREFIX.length()));
            return new ReservationExpiryKey(orderId, rawkey);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
