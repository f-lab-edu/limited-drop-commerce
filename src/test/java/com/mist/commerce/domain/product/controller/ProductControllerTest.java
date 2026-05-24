package com.mist.commerce.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.service.ProductService;
import com.mist.commerce.domain.user.service.CustomOAuth2UserService;
import com.mist.commerce.domain.user.service.TokenService;
import com.mist.commerce.global.config.ClockConfig;
import com.mist.commerce.global.config.OAuth2LoginFailureHandler;
import com.mist.commerce.global.config.OAuth2LoginSuccessHandler;
import com.mist.commerce.global.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, ClockConfig.class})
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Test
    @DisplayName("COMPANY가 유효한 본문으로 상품을 등록하면 생성 응답을 반환한다")
    void createProduct_withCompanyAndValidBody_returnsCreatedResponse() throws Exception {
        given(productService.createProduct(eq(10L), any(CreateProductRequest.class)))
                .willReturn(new CreateProductResponse(100L));

        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 1,
                                  "name": "Limited Sneakers",
                                  "description": "2026 한정판",
                                  "price": 150000,
                                  "status": "READY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.productId").value(100))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService).createProduct(eq(10L), any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("미인증 사용자가 상품 등록을 요청하면 인증 오류를 반환한다")
    void createProduct_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDraftBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    @DisplayName("COMPANY 권한이 없는 사용자가 상품 등록을 요청하면 접근 거부를 반환한다")
    void createProduct_withoutCompanyRole_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDraftBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    @DisplayName("상품명이 누락되면 검증 오류를 반환한다")
    void createProduct_whenNameMissing_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 1,
                                  "description": "2026 한정판",
                                  "price": 150000,
                                  "status": "READY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    @DisplayName("가격이 음수이면 검증 오류를 반환한다")
    void createProduct_whenPriceIsNegative_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 1,
                                  "name": "Limited Sneakers",
                                  "description": "2026 한정판",
                                  "price": -1,
                                  "status": "READY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'price')]").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    @DisplayName("알 수 없는 상품 상태 문자열이면 검증 오류를 반환한다")
    void createProduct_whenStatusIsUnknown_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 1,
                                  "name": "Limited Sneakers",
                                  "description": "2026 한정판",
                                  "price": 150000,
                                  "status": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService, never()).createProduct(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 ID로 상품 등록을 요청하면 브랜드 없음 오류를 반환한다")
    void createProduct_whenBrandDoesNotExist_returnsBrandNotFound() throws Exception {
        given(productService.createProduct(eq(10L), any(CreateProductRequest.class)))
                .willThrow(new BrandNotFoundException(999_999L));

        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 999999,
                                  "name": "Limited Sneakers",
                                  "description": "2026 한정판",
                                  "price": 150000,
                                  "status": "READY"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService).createProduct(eq(10L), any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("COMPANY가 상품 설명 없이 상품을 등록하면 생성 응답을 반환한다")
    void createProduct_withoutDescription_returnsCreatedResponse() throws Exception {
        given(productService.createProduct(eq(10L), any(CreateProductRequest.class)))
                .willReturn(new CreateProductResponse(100L));

        mockMvc.perform(post("/api/v1/products")
                        .with(authentication(authenticatedUser("ROLE_COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandId": 1,
                                  "name": "Limited Sneakers",
                                  "price": 150000,
                                  "status": "ON_SALE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data.productId").value(100))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
        verify(productService).createProduct(eq(10L), any(CreateProductRequest.class));
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(String authority) {
        return new UsernamePasswordAuthenticationToken(
                10L,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }

    private String validDraftBody() {
        return """
                {
                  "brandId": 1,
                  "name": "Limited Sneakers",
                  "description": "2026 한정판",
                  "price": 150000,
                  "status": "READY"
                }
                """;
    }
}
