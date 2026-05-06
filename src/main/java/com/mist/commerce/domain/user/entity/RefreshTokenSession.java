package com.mist.commerce.domain.user.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String sessionId;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private LocalDateTime lastUsedAt;

    private LocalDateTime expiredAt;

    public boolean isExpired() {
        return expiredAt != null && !expiredAt.isAfter(LocalDateTime.now());
    }

    public void revoke() {
        if (this.status == SessionStatus.REVOKED) {
            throw new IllegalStateException("Already revoked session");
        }
        this.status = SessionStatus.REVOKED;
    }
}
