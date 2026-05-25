package com.mist.commerce.domain.event.controller;

import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.dto.EventCreateResponse;
import com.mist.commerce.domain.event.service.EventService;
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
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService dropEventService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<ApiResponse<EventCreateResponse>> create(
            @Valid @RequestBody EventCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        EventCreateResponse data = dropEventService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "리소스가 생성되었습니다.", clock.instant()));
    }
}
