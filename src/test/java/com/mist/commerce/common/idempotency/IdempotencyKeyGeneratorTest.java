package com.mist.commerce.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyGeneratorTest {

    private final IdempotencyKeyGenerator keyGenerator = new IdempotencyKeyGenerator();

    @Test
    @DisplayName("scope와 idempotencyKey를 콜론으로 이어 Redis 키를 만든다")
    void generate_joinsScopeAndIdempotencyKeyWithColon() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .userId(10L)
                .scope("reservation")
                .idempotencyKey("idem-key-001")
                .fingerprint("fp")
                .build();

        String key = keyGenerator.generate(request);

        assertThat(key).isEqualTo("reservation:idem-key-001");
    }
}
