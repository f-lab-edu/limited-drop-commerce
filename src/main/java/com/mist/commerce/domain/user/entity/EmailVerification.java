package com.mist.commerce.domain.user.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime expiredAt;

    private LocalDateTime verifiedAt;

    public static EmailVerification of(String email, String verificationToken, LocalDateTime expiredAt) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.verificationToken = verificationToken;
        verification.expiredAt = expiredAt;
        return verification;
    }

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
