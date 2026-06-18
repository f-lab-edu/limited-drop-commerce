package com.mist.commerce.domain.reservation.controller;

import com.mist.commerce.domain.reservation.dto.ReservationRequest;
import com.mist.commerce.domain.reservation.dto.ReservationResponse;
import com.mist.commerce.domain.reservation.service.ReservationService;
import com.mist.commerce.domain.reservation.service.ReserveCommand;
import com.mist.commerce.domain.reservation.service.ReserveResult;
import com.mist.commerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
            @Valid @RequestBody ReservationRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        ReserveCommand command = new ReserveCommand(
                userId,
                request.eventId(),
                request.eventItemId(),
                request.eventItemOptionStockId(),
                request.quantity());
        ReserveResult result = reservationService.reserve(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ReservationResponse.from(result),
                        "리소스가 생성되었습니다.",
                        clock.instant()));
    }
}
