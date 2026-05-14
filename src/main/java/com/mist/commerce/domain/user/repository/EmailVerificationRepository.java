package com.mist.commerce.domain.user.repository;

import com.mist.commerce.domain.user.entity.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByVerificationToken(String token);

    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDescIdDesc(String email);
}
