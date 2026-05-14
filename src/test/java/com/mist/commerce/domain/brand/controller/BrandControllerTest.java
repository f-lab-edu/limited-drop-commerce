package com.mist.commerce.domain.brand.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mist.commerce.CommerceApplication;
import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.service.BrandService;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {CommerceApplication.class, BrandController.class, BrandControllerTest.MockMvcTestConfig.class})
class BrandControllerTest {

    @MockitoBean
    private BrandService brandService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("인증된 기업 사용자가 유효한 본문으로 등록하면 201과 브랜드 응답이 반환된다")
    void create_withAuthenticatedCompanyUserAndValidBody_returns201AndBrandResponse() throws Exception {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        BrandCreateResponse response = new BrandCreateResponse(
                42L,
                "Mist",
                "desc",
                7L,
                Instant.parse("2026-05-14T00:00:00Z")
        );
        given(brandService.create(eq(1L), any(BrandCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/brands")
                        .with(authAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.brandId").value(42))
                .andExpect(jsonPath("$.data.name").value("Mist"))
                .andExpect(jsonPath("$.data.description").value("desc"))
                .andExpect(jsonPath("$.data.companyId").value(7))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("name이 공백이면 400과 VALIDATION_ERROR를 반환한다")
    void create_withBlankName_returns400AndValidationError() throws Exception {
        BrandCreateRequest request = new BrandCreateRequest("", "desc");

        mockMvc.perform(post("/api/v1/brands")
                        .with(authAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());

        verify(brandService, never()).create(any(), any());
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환한다")
    void create_withoutAuthentication_returns401() throws Exception {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");

        mockMvc.perform(post("/api/v1/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(brandService, never()).create(any(), any());
    }

    @Test
    @DisplayName("서비스가 BrandRegistrationForbiddenException을 던지면 403과 BRAND_REGISTRATION_FORBIDDEN을 반환한다")
    void create_whenServiceThrowsForbidden_returns403AndForbiddenCode() throws Exception {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(brandService.create(eq(1L), any(BrandCreateRequest.class)))
                .willThrow(new BrandRegistrationForbiddenException(1L));

        mockMvc.perform(post("/api/v1/brands")
                        .with(authAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRAND_REGISTRATION_FORBIDDEN"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("서비스가 BrandNameDuplicatedException을 던지면 409와 BRAND_NAME_DUPLICATED를 반환한다")
    void create_whenServiceThrowsDuplicate_returns409AndDuplicatedCode() throws Exception {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(brandService.create(eq(1L), any(BrandCreateRequest.class)))
                .willThrow(new BrandNameDuplicatedException(7L, "Mist"));

        mockMvc.perform(post("/api/v1/brands")
                        .with(authAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRAND_NAME_DUPLICATED"))
                .andExpect(jsonPath("$.success").value(false));
    }

    private static RequestPostProcessor authAs(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    @TestConfiguration
    static class MockMvcTestConfig {

        @Bean
        MockMvc mockMvc(WebApplicationContext context) {
            return MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();
        }
    }
}
