package com.mist.commerce.common.idempotency.web;

import com.mist.commerce.common.idempotency.model.IdempotencyRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface IdempotencyRequestResolver {

    boolean supports(HttpServletRequest request);

    IdempotencyRequest resolve(HttpServletRequest request) throws IOException;
}
