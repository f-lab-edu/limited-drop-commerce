package com.mist.commerce.global.config;

import com.mist.commerce.domain.reservation.redis.ReservationExpiryEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisKeyExpirationConfig {

    private static final String EXPIRED_KEYEVENT_PATTERN = "__keyevent@*__:expired";

    @Bean
    public RedisMessageListenerContainer reservationExpiryListenerContainer(
            RedisConnectionFactory connectionFactory,
            ReservationExpiryEventListener listener) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().setConfig("notify-keyspace-events", "Ex");
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new PatternTopic(EXPIRED_KEYEVENT_PATTERN));
        return container;
    }
}
