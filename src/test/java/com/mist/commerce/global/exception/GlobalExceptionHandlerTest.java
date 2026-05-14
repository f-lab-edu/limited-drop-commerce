package com.mist.commerce.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.global.response.ApiResponse;
import com.mist.commerce.global.response.ErrorDetail;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final String EXPECTED_TIMESTAMP = "2026-05-09T10:00:00.000Z";

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        handler = new GlobalExceptionHandler(fixedClock);
    }

    // ===== 8.1 도메인 예외 매핑 =====

    @Test
    void handle_UserEmailDuplicatedException_409_USER_EMAIL_DUPLICATED() {
        UserEmailDuplicatedException ex = new UserEmailDuplicatedException("a@b.com");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUserEmailDuplicated(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.code()).isEqualTo("USER_EMAIL_DUPLICATED");
        assertThat(body.message()).contains("a@b.com");
        assertThat(body.data()).isNull();
        assertThat(body.errors()).isNull();
        assertThat(body.timestamp()).isEqualTo(EXPECTED_TIMESTAMP);
    }

    @Test
    void handle_OAuthAccountAlreadyLinkedToBusinessException_409() {
        OAuthAccountAlreadyLinkedToBusinessException ex = new OAuthAccountAlreadyLinkedToBusinessException();

        ResponseEntity<ApiResponse<Void>> response = handler.handleOAuthAccountAlreadyLinkedToBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("OAUTH_ACCOUNT_ALREADY_LINKED_TO_BUSINESS");
        assertThat(body.errors()).isNull();
    }

    @Test
    void handle_InvalidTokenException_401_INVALID_TOKEN() {
        InvalidTokenException ex = new InvalidTokenException();

        ResponseEntity<ApiResponse<Void>> response = handler.handleInvalidToken(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INVALID_TOKEN");
        assertThat(body.errors()).isNull();
    }

    // ===== 8.2 Validation 매핑 =====

    @Test
    void handle_MethodArgumentNotValidException_400_VALIDATION_ERROR_errors_채워짐() {
        MethodArgumentNotValidException ex = givenValidationException(
                new FieldError("dto", "price", -1000, false, null, null, "가격은 0 이상이어야 합니다."),
                new FieldError("dto", "name", "", false, null, null, "상품명은 필수입니다.")
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.errors()).hasSize(2);
        assertThat(body.errors()).extracting(ErrorDetail::field).containsExactlyInAnyOrder("price", "name");
        assertThat(body.errors())
                .filteredOn(d -> "price".equals(d.field()))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.value()).isEqualTo(-1000);
                    assertThat(d.reason()).isEqualTo("가격은 0 이상이어야 합니다.");
                });
        assertThat(body.errors())
                .filteredOn(d -> "name".equals(d.field()))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.value()).isEqualTo("");
                    assertThat(d.reason()).isEqualTo("상품명은 필수입니다.");
                });
    }

    @Test
    void handle_validation_단일_필드_오류도_errors_배열로_반환() {
        MethodArgumentNotValidException ex = givenValidationException(
                new FieldError("dto", "email", "bad", false, null, null, "이메일 형식이 올바르지 않습니다.")
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).hasSize(1);
    }

    // ===== 8.3 Fallback / 봉투 형식 =====

    @Test
    void handle_미처리_RuntimeException_500_INTERNAL_SERVER_ERROR() {
        RuntimeException ex = new RuntimeException("db connection lost");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAny(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.message()).doesNotContain("db connection lost");
        assertThat(body.errors()).isNull();
        assertThat(body.data()).isNull();
    }

    @Test
    void timestamp_Clock_고정값으로_결정적() {
        UserEmailDuplicatedException ex1 = new UserEmailDuplicatedException("a@b.com");
        UserEmailDuplicatedException ex2 = new UserEmailDuplicatedException("a@b.com");

        ResponseEntity<ApiResponse<Void>> first = handler.handleUserEmailDuplicated(ex1);
        ResponseEntity<ApiResponse<Void>> second = handler.handleUserEmailDuplicated(ex2);

        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().timestamp()).isEqualTo(first.getBody().timestamp());
        assertThat(first.getBody().timestamp()).isEqualTo(EXPECTED_TIMESTAMP);
    }

    private MethodArgumentNotValidException givenValidationException(FieldError... fieldErrors) {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        for (FieldError fe : fieldErrors) {
            bindingResult.addError(fe);
        }
        MethodParameter parameter = stubMethodParameter();
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    private MethodParameter stubMethodParameter() {
        try {
            return new MethodParameter(
                    GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", Object.class),
                    0
            );
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    void dummy(Object input) {
    }
}
