package com.mist.commerce.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.user.dto.AuthTokenResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class ApiResponseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-09T10:00:00Z");
    private static final Instant FIXED_NOW_WITH_MS = Instant.parse("2026-05-09T10:00:00.123Z");
    private static final String EXPECTED_TIMESTAMP = "2026-05-09T10:00:00.000Z";
    private static final String EXPECTED_TIMESTAMP_MS = "2026-05-09T10:00:00.123Z";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void success_정상_data_message_봉투_생성() {
        AuthTokenResponse data = new AuthTokenResponse("at", "rt", true);

        ApiResponse<AuthTokenResponse> response = ApiResponse.success(data, "로그인에 성공했습니다.", FIXED_NOW);

        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("로그인에 성공했습니다.");
        assertThat(response.data()).isEqualTo(data);
        assertThat(response.errors()).isNull();
        assertThat(response.timestamp()).isEqualTo(EXPECTED_TIMESTAMP);
    }

    @Test
    void success_data_null_허용() {
        ApiResponse<Object> response = ApiResponse.success(null, "조회 결과가 없습니다.", FIXED_NOW);

        assertThat(response.data()).isNull();
        assertThat(response.success()).isTrue();
        assertThat(response.code()).isEqualTo("OK");
    }

    @Test
    void fail_단건_도메인_오류_봉투_생성() {
        ApiResponse<Void> response = ApiResponse.fail(
                "USER_EMAIL_DUPLICATED",
                "이미 등록된 이메일입니다.",
                FIXED_NOW
        );

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("USER_EMAIL_DUPLICATED");
        assertThat(response.message()).isEqualTo("이미 등록된 이메일입니다.");
        assertThat(response.data()).isNull();
        assertThat(response.errors()).isNull();
    }

    @Test
    void fail_validation_errors_리스트_보존() {
        List<ErrorDetail> errors = List.of(
                new ErrorDetail("price", -1000, "가격은 0 이상이어야 합니다."),
                new ErrorDetail("name", "", "상품명은 필수입니다.")
        );

        ApiResponse<Void> response = ApiResponse.fail(
                "VALIDATION_ERROR",
                "입력값이 올바르지 않습니다.",
                errors,
                FIXED_NOW
        );

        assertThat(response.errors()).hasSize(2);
        assertThat(response.errors().get(0).field()).isEqualTo("price");
        assertThat(response.errors().get(0).value()).isEqualTo(-1000);
        assertThat(response.errors().get(0).reason()).isEqualTo("가격은 0 이상이어야 합니다.");
        assertThat(response.errors().get(1).field()).isEqualTo("name");
        assertThat(response.errors().get(1).value()).isEqualTo("");
        assertThat(response.errors().get(1).reason()).isEqualTo("상품명은 필수입니다.");
        assertThat(response.data()).isNull();
    }

    @Test
    void timestamp_yyyy_MM_dd_HH_mm_ss_SSS_Z_포맷() {
        ApiResponse<String> response = ApiResponse.success("data", "ok", FIXED_NOW_WITH_MS);

        assertThat(response.timestamp()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");
        assertThat(response.timestamp()).isEqualTo(EXPECTED_TIMESTAMP_MS);
    }

    @Test
    void Jackson_직렬화_camelCase_필드명_errors_null도_필드_누락_없음() {
        AuthTokenResponse data = new AuthTokenResponse("at", "rt", true);
        ApiResponse<AuthTokenResponse> response = ApiResponse.success(data, "ok", FIXED_NOW);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"code\":\"OK\"");
        assertThat(json).contains("\"data\":");
        assertThat(json).contains("\"errors\":null");
        assertThat(json).contains("\"timestamp\":\"" + EXPECTED_TIMESTAMP + "\"");
    }

    @Test
    void 같은_Instant_입력이면_timestamp_동일() {
        ApiResponse<String> first = ApiResponse.success("a", "ok", FIXED_NOW);
        ApiResponse<String> second = ApiResponse.success("a", "ok", FIXED_NOW);

        assertThat(second.timestamp()).isEqualTo(first.timestamp());
    }

    @Test
    void 역직렬화_TypeReference로_타입_안전_검증() {
        AuthTokenResponse original = new AuthTokenResponse("at", "rt", true);
        String json = objectMapper.writeValueAsString(
                ApiResponse.success(original, "로그인에 성공했습니다.", FIXED_NOW)
        );

        ApiResponse<AuthTokenResponse> parsed = objectMapper.readValue(
                json, new TypeReference<ApiResponse<AuthTokenResponse>>() {}
        );

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.code()).isEqualTo("OK");
        assertThat(parsed.data()).isEqualTo(original);
        assertThat(parsed.errors()).isNull();
        assertThat(parsed.timestamp()).isEqualTo(EXPECTED_TIMESTAMP);
    }
}
