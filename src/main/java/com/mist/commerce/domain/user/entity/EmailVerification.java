package com.mist.commerce.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime expiredAt;

    private LocalDateTime verifiedAt;

    public boolean isExpired() {
        return expiredAt != null && !expiredAt.isAfter(LocalDateTime.now());
    }

    public void verify() {
        if (this.verifiedAt != null) {
            throw new IllegalStateException("Already verified email");
        }
        if (isExpired()) {
            throw new IllegalStateException("Expired verification token");
        }
        this.verifiedAt = LocalDateTime.now();
    }
}
