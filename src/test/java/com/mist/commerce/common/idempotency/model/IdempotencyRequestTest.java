package com.mist.commerce.common.idempotency.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyRequestTest {

    @Test
    @DisplayName("scope와 idempotencyKey를 콜론으로 이어 Redis 키를 만든다")
    void generate_joinsScopeAndIdempotencyKeyWithColon() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .userId(10L)
                .scope("reservation")
                .idempotencyKey("idem-key-001")
                .fingerprint("fp")
                .build();

        String key = request.generate();

        assertThat(key).isEqualTo("reservation:idem-key-001");
    }
}
