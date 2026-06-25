package com.mist.commerce.domain.order.controller;

import com.mist.commerce.domain.order.dto.OrderCancelResponse;
import com.mist.commerce.domain.order.service.CancelCommand;
import com.mist.commerce.domain.order.service.CancelResult;
import com.mist.commerce.domain.order.service.OrderCancelService;
import com.mist.commerce.global.response.ApiResponse;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCancelService orderCancelService;
    private final Clock clock;

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderCancelResponse>> cancel(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Long userId
    ) {
        CancelResult result = orderCancelService.cancel(new CancelCommand(userId, orderId, idempotencyKey));

        return ResponseEntity.ok(ApiResponse.success(
                OrderCancelResponse.from(result),
                "주문이 취소되었습니다.",
                clock.instant()));
    }
}
