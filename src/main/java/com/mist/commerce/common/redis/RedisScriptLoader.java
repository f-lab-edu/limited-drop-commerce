package com.mist.commerce.common.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisScriptLoader {
    public RedisScript<String> loadStringScript(String path) {
        return loadScript(path, String.class);
    }

    public RedisScript<Long> loadLongScript(String path) {
        return loadScript(path, Long.class);
    }

    public RedisScript<Boolean> loadBooleanScript(String path) {
        return loadScript(path, Boolean.class);
    }

    public <T> RedisScript<T> loadScript(String path, Class<T> resultType) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            if (!resource.exists()) {
                throw new IllegalArgumentException("Redis script file not found: " + path);
            }

            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            DefaultRedisScript<T> redisScript = new DefaultRedisScript<>();

            redisScript.setScriptText(script);
            redisScript.setResultType(resultType);
            return redisScript;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Redis script: " + path, e);
        }

    }
}
