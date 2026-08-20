package com.mist.commerce.global.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CharsetUtils {
    private  CharsetUtils() {}

    public static Charset getCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }

        return Charset.forName(encoding);
    }
}
