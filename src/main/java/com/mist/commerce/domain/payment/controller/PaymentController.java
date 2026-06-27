package com.mist.commerce.domain.payment.controller;

import com.mist.commerce.domain.payment.dto.PaymentRequest;
import com.mist.commerce.domain.payment.dto.PaymentResponse;
import com.mist.commerce.domain.payment.service.PaymentCommand;
import com.mist.commerce.domain.payment.service.PaymentResult;
import com.mist.commerce.domain.payment.service.PaymentService;
import com.mist.commerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @RequestBody @Valid PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Long userId
    ) {
        PaymentResult result = paymentService.pay(new PaymentCommand(
                userId,
                request.orderId(),
                request.paymentKey(),
                request.amount(),
                idempotencyKey));

        return ResponseEntity.ok(ApiResponse.success(
                PaymentResponse.from(result),
                "결제가 완료되었습니다.",
                clock.instant()));
    }
}
