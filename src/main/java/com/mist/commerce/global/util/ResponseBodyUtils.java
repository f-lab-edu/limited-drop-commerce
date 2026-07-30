package com.mist.commerce.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

public final class ResponseBodyUtils {
    private  ResponseBodyUtils() {}

    public static String readCachedBody(HttpServletResponse response) {
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);

        if (wrapper == null) {
            throw new IllegalStateException("ContentCachingResponseWrapper is required.");
        }

        byte[] content = wrapper.getContentAsByteArray();

        if (content.length == 0) {
            return "";
        }

        return new String(content, CharsetUtils.getCharset(wrapper.getCharacterEncoding()));
    }
}
