package com.mist.commerce.domain.event.controller;

import static org.hamcrest.Matchers.nullValue;
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
import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.dto.EventCreateResponse;
import com.mist.commerce.domain.event.exception.EventRegistrationForbiddenException;
import com.mist.commerce.domain.event.exception.EventScheduleValidationException;
import com.mist.commerce.domain.event.service.EventService;
import com.mist.commerce.domain.product.exception.ProductNotFoundException;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {
        CommerceApplication.class,
        EventController.class,
        EventControllerTest.MockMvcTestConfig.class
})
class EventControllerTest extends MySqlContainerTestSupport {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");

    @MockitoBean
    private EventService dropEventService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static RequestPostProcessor authAs(Long userId, String authority) {
        return authentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test
    @DisplayName("인증된 사용자가 optionGroupId 포함 유효 본문으로 등록하면 201과 기존 성공 envelope를 반환한다")
    void create_withAuthenticatedUserAndValidBody_returns201AndSuccessEnvelope() throws Exception {
        EventCreateRequest request = validRequest();
        EventCreateResponse response = new EventCreateResponse(
                42L,
                "READY",
                List.of(new EventCreateResponse.ResponseItem(101L, 10L)),
                OffsetDateTime.parse("2026-06-01T00:30:00+09:00")
        );
        given(dropEventService.create(eq(1L), any(EventCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.data.eventId").value(42))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.items[0].eventItemId").value(101))
                .andExpect(jsonPath("$.data.items[0].productId").value(10))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("items[].optionStocks[].optionGroupId 누락 시 400 VALIDATION_ERROR envelope를 반환한다")
    void create_withMissingOptionGroupId_returns400ValidationErrorAndDoesNotCallService() throws Exception {
        String requestBody = """
                {
                  "brandId": 1,
                  "title": "한정 스니커즈 드롭",
                  "startAt": "2026-06-01T10:00:00Z",
                  "endAt": "2026-06-01T12:00:00Z",
                  "items": [
                    {
                      "productId": 10,
                      "price": 150000,
                      "quantity": 100,
                      "maxPurchasePerCustomer": 10,
                      "optionStocks": [
                        {
                          "optionValueId": 5,
                          "stockQuantity": 40
                        }
                      ]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].optionStocks[0].optionGroupId')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("인증 없이 요청하면 401을 반환하고 서비스를 호출하지 않는다")
    void create_withoutAuthentication_returns401AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("title이 공백이면 400 VALIDATION_ERROR와 title 필드 오류를 반환한다")
    void create_withBlankTitle_returns400ValidationErrorWithErrors() throws Exception {
        EventCreateRequest request = new EventCreateRequest(
                1L,
                "",
                START_AT,
                END_AT,
                List.of(validItem())
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("items가 비어 있으면 400 VALIDATION_ERROR와 items 필드 오류를 반환한다")
    void create_withEmptyItems_returns400ValidationErrorWithErrors() throws Exception {
        EventCreateRequest request = new EventCreateRequest(1L, "한정 스니커즈 드롭", START_AT, END_AT, List.of());

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("quantity만 0이면 400 VALIDATION_ERROR와 quantity 필드 오류를 반환한다")
    void create_withZeroQuantity_returns400ValidationErrorForQuantityOnly() throws Exception {
        EventCreateRequest.Item invalidItem = new EventCreateRequest.Item(
                10L,
                new BigDecimal("150000"),
                0,
                10,
                List.of(validOptionStock())
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(List.of(invalidItem)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].quantity')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("stockQuantity만 음수이면 400 VALIDATION_ERROR와 stockQuantity 필드 오류를 반환한다")
    void create_withNegativeStockQuantity_returns400ValidationErrorForStockQuantityOnly() throws Exception {
        EventCreateRequest.OptionStock invalidStock = new EventCreateRequest.OptionStock(3L, 5L, -1);
        EventCreateRequest.Item invalidItem = new EventCreateRequest.Item(
                10L,
                new BigDecimal("150000"),
                100,
                10,
                List.of(invalidStock)
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(List.of(invalidItem)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].optionStocks[0].stockQuantity')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("price만 음수이면 400 VALIDATION_ERROR와 price 필드 오류를 반환한다")
    void create_withNegativePrice_returns400ValidationErrorForPriceOnly() throws Exception {
        EventCreateRequest.Item invalidItem = new EventCreateRequest.Item(
                10L,
                new BigDecimal("-1"),
                100,
                10,
                List.of(validOptionStock())
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(List.of(invalidItem)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].price')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("price만 null이면 400 VALIDATION_ERROR와 price 필드 오류를 반환한다")
    void create_withNullPrice_returns400ValidationErrorForPrice() throws Exception {
        EventCreateRequest.Item invalidItem = new EventCreateRequest.Item(
                10L,
                null,
                100,
                10,
                List.of(validOptionStock())
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(List.of(invalidItem)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].price')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("title이 201자이면 400 VALIDATION_ERROR와 title 필드 오류를 반환한다")
    void create_withTitleOverMaxLength_returns400ValidationErrorForTitleSize() throws Exception {
        EventCreateRequest request = new EventCreateRequest(
                1L,
                "A".repeat(201),
                START_AT,
                END_AT,
                List.of(validItem())
        );

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());

        verify(dropEventService, never()).create(any(), any());
    }

    @Test
    @DisplayName("서비스가 EventRegistrationForbiddenException을 던지면 403과 EVENT_REGISTRATION_FORBIDDEN을 반환한다")
    void create_whenServiceThrowsForbidden_returns403EventRegistrationForbidden() throws Exception {
        given(dropEventService.create(eq(1L), any(EventCreateRequest.class)))
                .willThrow(new EventRegistrationForbiddenException(1L));

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_FORBIDDEN"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("서비스가 ProductNotFoundException을 던지면 404와 PRODUCT_NOT_FOUND를 반환한다")
    void create_whenServiceThrowsProductNotFound_returns404ProductNotFound() throws Exception {
        given(dropEventService.create(eq(1L), any(EventCreateRequest.class)))
                .willThrow(new ProductNotFoundException(10L));

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("서비스가 일정 검증 예외를 던지면 400 VALIDATION_ERROR와 errors null을 반환한다")
    void create_whenServiceThrowsScheduleValidation_returns400ValidationErrorWithoutErrors() throws Exception {
        given(dropEventService.create(eq(1L), any(EventCreateRequest.class)))
                .willThrow(new EventScheduleValidationException());

        mockMvc.perform(post("/api/v1/events")
                        .with(authAs(1L, "ROLE_COMPANY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVENT_SCHEDULE_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors").exists());
    }

    private EventCreateRequest validRequest() {
        return request(List.of(validItem()));
    }

    private EventCreateRequest request(List<EventCreateRequest.Item> items) {
        return new EventCreateRequest(1L, "한정 스니커즈 드롭", START_AT, END_AT, items);
    }

    private EventCreateRequest.Item validItem() {
        return new EventCreateRequest.Item(
                10L,
                new BigDecimal("150000"),
                100,
                10,
                List.of(validOptionStock())
        );
    }

    private EventCreateRequest.OptionStock validOptionStock() {
        return new EventCreateRequest.OptionStock(3L, 5L, 40);
    }

    @TestConfiguration
    static class MockMvcTestConfig {

        @Bean
        MockMvc mockMvc(WebApplicationContext context) {
            return MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
