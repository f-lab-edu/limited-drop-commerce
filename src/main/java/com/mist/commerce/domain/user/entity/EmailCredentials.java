package com.mist.commerce.domain.user.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
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
public class EmailCredentials extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    private String password;

    private LocalDateTime verifiedAt;

    public static EmailCredentials of(String email, String encodedPassword) {
        EmailCredentials emailCredentials = new EmailCredentials();
        emailCredentials.email = email;
        emailCredentials.password = encodedPassword;
        return emailCredentials;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
