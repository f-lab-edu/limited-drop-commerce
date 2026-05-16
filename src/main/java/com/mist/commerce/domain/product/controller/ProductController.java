package com.mist.commerce.domain.product.controller;

import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.service.ProductService;
import com.mist.commerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String CREATED_MESSAGE = "리소스가 생성되었습니다.";

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateProductResponse>> createProduct(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateProductRequest request
    ) {
        CreateProductResponse response = productService.createProduct(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, CREATED_MESSAGE, Instant.now()));
    }
}
