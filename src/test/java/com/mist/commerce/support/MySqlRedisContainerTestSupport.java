package com.mist.commerce.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles({"test", "test-mysql"})
public abstract class MySqlRedisContainerTestSupport extends RedisContainerTestSupport {

    @DynamicPropertySource
    static void overrideMySqlProps(DynamicPropertyRegistry registry) {
        MySqlContainerTestSupport.overrideProps(registry);
    }
}
