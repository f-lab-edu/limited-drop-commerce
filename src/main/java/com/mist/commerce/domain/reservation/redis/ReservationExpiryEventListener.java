package com.mist.commerce.domain.reservation.redis;

import com.mist.commerce.domain.reservation.service.ExpiryRecoveryService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryEventListener implements MessageListener {

    private final ExpiryRecoveryService expiryRecoveryService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        String orderId = ReservationExpiryRedisRepository.orderIdFromKey(expiredKey);
        if (orderId == null) {
            return;
        }

        try {
            expiryRecoveryService.recover(Long.parseLong(orderId));
        } catch (Exception e) {
            log.warn("Failed to recover order on expiry event. key={}", expiredKey, e);
        }
    }
}
