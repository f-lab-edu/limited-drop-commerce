package com.mist.commerce.domain.user.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_token_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, unique = true)
    private String sessionId;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private LocalDateTime lastUsedAt;

    private LocalDateTime expiredAt;

    public static RefreshTokenSession of(
            Long userId,
            String sessionId,
            SessionStatus status,
            LocalDateTime expiredAt
    ) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.userId = userId;
        session.sessionId = sessionId;
        session.status = status;
        session.expiredAt = expiredAt;
        return session;
    }

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
