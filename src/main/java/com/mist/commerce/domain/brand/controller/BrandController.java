package com.mist.commerce.domain.brand.controller;

import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.service.BrandService;
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
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final Clock clock;

    @PostMapping
    public ResponseEntity<ApiResponse<BrandCreateResponse>> create(
            @Valid @RequestBody BrandCreateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        BrandCreateResponse data = brandService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "리소스가 생성되었습니다.", clock.instant()));
    }
}
