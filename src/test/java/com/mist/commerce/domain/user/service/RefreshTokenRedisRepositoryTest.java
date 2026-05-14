package com.mist.commerce.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class RefreshTokenRedisRepositoryTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RefreshTokenRedisRepository redisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @AfterEach
    void cleanup() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void save_sessionId_tokenValue_저장_후_find_정상_조회() {
        // given
        String sessionId = "uuid-abc";
        String tokenValue = "refresh-token-stub";

        // when
        redisRepository.save(sessionId, tokenValue, 1_209_600L);
        Optional<String> found = redisRepository.find(sessionId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(tokenValue);
    }

    @Test
    void find_없는_키이면_empty() {
        // given

        // when
        Optional<String> found = redisRepository.find("uuid-none");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void save_TTL_적용_후_만료되면_find_empty() throws InterruptedException {
        // given
        redisRepository.save("uuid-abc", "refresh-token-stub", 1L);

        // when
        Thread.sleep(1500L);
        Optional<String> found = redisRepository.find("uuid-abc");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void save_TTL_조회_정상() {
        // given
        redisRepository.save("uuid-abc", "refresh-token-stub", 1_209_600L);

        // when
        Long ttl = redisTemplate.getExpire("refresh:uuid-abc");

        // then
        assertThat(ttl).isGreaterThanOrEqualTo(1_209_590L);
    }

    @Test
    void save_동일_sessionId_재저장_시_값_덮어쓰기() {
        // given
        redisRepository.save("uuid-abc", "old-token", 1_209_600L);

        // when
        redisRepository.save("uuid-abc", "new-token", 1_209_600L);
        Optional<String> found = redisRepository.find("uuid-abc");

        // then
        assertThat(found).contains("new-token");
    }

    @Test
    void delete_저장된_키_제거() {
        // given
        redisRepository.save("uuid-abc", "refresh-token-stub", 1_209_600L);

        // when
        redisRepository.delete("uuid-abc");
        Optional<String> found = redisRepository.find("uuid-abc");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void delete_존재하지_않는_키_예외_없음() {
        // given

        // when & then
        assertThatCode(() -> redisRepository.delete("uuid-none"))
                .doesNotThrowAnyException();
    }

    @Test
    void key_prefix_네임스페이스_확인() {
        // given
        redisRepository.save("uuid-abc", "v", 1_209_600L);

        // when
        Boolean hasPrefixedKey = redisTemplate.hasKey("refresh:uuid-abc");
        Boolean hasRawKey = redisTemplate.hasKey("uuid-abc");

        // then
        assertThat(hasPrefixedKey).isTrue();
        assertThat(hasRawKey).isFalse();
    }

    @SpringBootConfiguration
    @Import(RefreshTokenRedisRepository.class)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class TestConfig {
    }
}
