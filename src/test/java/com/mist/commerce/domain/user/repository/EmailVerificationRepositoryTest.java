package com.mist.commerce.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import com.mist.commerce.domain.user.entity.EmailVerification;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class EmailVerificationRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private EmailVerificationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByVerificationToken_존재하면_반환() {
        // given
        entityManager.persistAndFlush(verification("a@test.com", "token-abc"));

        // when
        Optional<EmailVerification> found = repository.findByVerificationToken("token-abc");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getVerificationToken()).isEqualTo("token-abc");
    }

    @Test
    void findByVerificationToken_없으면_empty() {
        // given

        // when
        Optional<EmailVerification> found = repository.findByVerificationToken("token-xyz");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void verificationToken_unique_constraint_중복_저장_예외() {
        // given
        repository.saveAndFlush(verification("a@test.com", "token-abc"));
        EmailVerification duplicate = verification("b@test.com", "token-abc");

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findTopByEmailOrderByCreatedAtDescIdDesc_가장_최근_레코드_반환() throws InterruptedException {
        // given
        persistAndSeparateByTime(verification("a@test.com", "token-1"));
        persistAndSeparateByTime(verification("a@test.com", "token-2"));
        persistAndSeparateByTime(verification("a@test.com", "token-3"));

        // when
        Optional<EmailVerification> found = repository.findTopByEmailOrderByCreatedAtDescIdDesc("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getVerificationToken()).isEqualTo("token-3");
    }

    @Test
    void findTopByEmailOrderByCreatedAtDescIdDesc_없으면_empty() {
        // given

        // when
        Optional<EmailVerification> found = repository.findTopByEmailOrderByCreatedAtDescIdDesc("none@test.com");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findTopByEmailOrderByCreatedAtDescIdDesc_다른_email_제외() throws InterruptedException {
        // given
        persistAndSeparateByTime(verification("a@test.com", "token-1"));
        persistAndSeparateByTime(verification("b@test.com", "token-2"));
        persistAndSeparateByTime(verification("a@test.com", "token-3"));

        // when
        Optional<EmailVerification> found = repository.findTopByEmailOrderByCreatedAtDescIdDesc("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@test.com");
        assertThat(found.get().getVerificationToken()).isEqualTo("token-3");
    }

    @Test
    void findTopByEmailOrderByCreatedAtDescIdDesc_createdAt_동률이면_id_DESC로_반환() {
        // given
        EmailVerification first = entityManager.persistAndFlush(verification("a@test.com", "token-1"));
        EmailVerification second = entityManager.persistAndFlush(verification("a@test.com", "token-2"));
        EmailVerification third = entityManager.persistAndFlush(verification("a@test.com", "token-3"));
        updateCreatedAtToSameTime(first, second, third);

        // when
        Optional<EmailVerification> found = repository.findTopByEmailOrderByCreatedAtDescIdDesc("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getVerificationToken()).isEqualTo("token-3");
    }

    @Test
    void findTopByEmailOrderByCreatedAtDescIdDesc_다른_email의_더_큰_id는_제외() {
        // given
        EmailVerification first = entityManager.persistAndFlush(verification("a@test.com", "token-1"));
        EmailVerification second = entityManager.persistAndFlush(verification("a@test.com", "token-2"));
        EmailVerification third = entityManager.persistAndFlush(verification("b@test.com", "token-3"));
        updateCreatedAtToSameTime(first, second, third);

        // when
        Optional<EmailVerification> found = repository.findTopByEmailOrderByCreatedAtDescIdDesc("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@test.com");
        assertThat(found.get().getVerificationToken()).isEqualTo("token-2");
    }

    @Test
    void save_verifiedAt_null_저장_허용() {
        // given
        EmailVerification verification = verification("a@test.com", "token-abc");

        // when
        EmailVerification saved = repository.saveAndFlush(verification);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVerifiedAt()).isNull();
    }

    @Test
    void save_expiredAt_필수_저장() {
        // given
        EmailVerification verification = EmailVerification.of(
                "a@test.com",
                "token-abc",
                LocalDateTime.now().plusMinutes(30)
        );

        // when
        EmailVerification saved = repository.saveAndFlush(verification);

        // then
        assertThat(saved.getExpiredAt()).isNotNull();
        assertThat(saved.isExpired()).isFalse();
    }

    private void persistAndSeparateByTime(EmailVerification verification) throws InterruptedException {
        entityManager.persistAndFlush(verification);
        Thread.sleep(10L);
        entityManager.clear();
    }

    private void updateCreatedAtToSameTime(EmailVerification... verifications) {
        for (EmailVerification verification : verifications) {
            entityManager.getEntityManager()
                    .createNativeQuery("update email_verifications set created_at = ? where id = ?")
                    .setParameter(1, "2026-05-07 00:00:00")
                    .setParameter(2, verification.getId())
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
    }

    private EmailVerification verification(String email, String token) {
        return EmailVerification.of(email, token, LocalDateTime.now().plusMinutes(30));
    }
}
