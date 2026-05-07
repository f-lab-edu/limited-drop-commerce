package com.mist.commerce.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import com.mist.commerce.domain.user.entity.LoginHistory;
import com.mist.commerce.domain.user.entity.LoginType;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class LoginHistoryRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private LoginHistoryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void save_구글_로그인_성공_이력_저장() {
        // given
        LoginHistory history = LoginHistory.success(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0");

        // when
        LoginHistory saved = repository.saveAndFlush(history);
        entityManager.clear();
        LoginHistory found = repository.findById(saved.getId()).get();

        // then
        assertThat(found.getId()).isNotNull();
        assertThat(found.getMemberId()).isEqualTo(1L);
        assertThat(found.getLoginType()).isEqualTo(LoginType.GOOGLE);
        assertThat(found.getSuccessYn()).isEqualTo("Y");
        assertThat(found.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(found.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(found.getFailureReason()).isNull();
    }

    @Test
    void save_로그인_실패_이력_저장() {
        // given
        LoginHistory history = LoginHistory.failure(
                1L,
                LoginType.GOOGLE,
                "127.0.0.1",
                "Mozilla/5.0",
                "USER_EMAIL_DUPLICATED"
        );

        // when
        LoginHistory saved = repository.saveAndFlush(history);

        // then
        assertThat(saved.getSuccessYn()).isEqualTo("N");
        assertThat(saved.getFailureReason()).isEqualTo("USER_EMAIL_DUPLICATED");
    }

    @Test
    void save_이메일_로그인_타입_저장() {
        // given
        LoginHistory history = LoginHistory.success(1L, LoginType.EMAIL, "127.0.0.1", "Mozilla/5.0");

        // when
        LoginHistory saved = repository.saveAndFlush(history);

        // then
        assertThat(saved.getLoginType()).isEqualTo(LoginType.EMAIL);
    }

    @Test
    void save_ipAddress_userAgent_null_허용() {
        // given
        LoginHistory history = LoginHistory.success(1L, LoginType.GOOGLE, null, null);

        // when
        LoginHistory saved = repository.saveAndFlush(history);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    void save_동일_memberId_복수_이력_누적() {
        // given
        repository.save(LoginHistory.success(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0"));
        repository.save(LoginHistory.failure(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0", "ERROR"));
        repository.save(LoginHistory.success(1L, LoginType.EMAIL, "127.0.0.1", "Mozilla/5.0"));
        repository.flush();

        // when
        List<LoginHistory> result = repository.findAll();

        // then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(history -> history.getMemberId().equals(1L));
    }

    @Test
    void save_createdAt_자동_설정() {
        // given
        LoginHistory history = LoginHistory.success(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0");

        // when
        LoginHistory saved = repository.saveAndFlush(history);

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void save_loginType_enum_STRING_저장_확인() {
        // given
        LoginHistory saved = repository.saveAndFlush(
                LoginHistory.success(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0")
        );
        entityManager.clear();

        // when
        Object loginType = entityManager.getEntityManager()
                .createNativeQuery("select login_type from login_histories where id = ?")
                .setParameter(1, saved.getId())
                .getSingleResult();

        // then
        assertThat(loginType).isEqualTo("GOOGLE");
    }
}
