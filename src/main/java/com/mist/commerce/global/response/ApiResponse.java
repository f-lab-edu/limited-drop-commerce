package com.mist.commerce.global.response;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        List<ErrorDetail> errors,
        String timestamp
) {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public static <T> ApiResponse<T> success(T data, String message, Instant now) {
        return new ApiResponse<>(true, "OK", message, data, null, format(now));
    }

    public static ApiResponse<Void> fail(String code, String message, Instant now) {
        return new ApiResponse<>(false, code, message, null, null, format(now));
    }

    public static ApiResponse<Void> fail(String code, String message, List<ErrorDetail> errors, Instant now) {
        return new ApiResponse<>(false, code, message, null, errors, format(now));
    }

    private static String format(Instant now) {
        return TIMESTAMP_FORMATTER.format(now);
    }
}
