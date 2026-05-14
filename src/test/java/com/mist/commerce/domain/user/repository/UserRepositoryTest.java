package com.mist.commerce.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
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
class UserRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private UserRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByEmail_존재하는_이메일이면_User_반환() {
        // given
        entityManager.persistAndFlush(User.createPersonal("a@test.com", "홍길동"));

        // when
        Optional<User> found = repository.findByEmail("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("a@test.com");
        assertThat(found.get().getName()).isEqualTo("홍길동");
    }

    @Test
    void findByEmail_없는_이메일이면_empty_반환() {
        // given

        // when
        Optional<User> found = repository.findByEmail("none@test.com");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_대소문자_구분_매칭() {
        // given
        entityManager.persistAndFlush(User.createPersonal("A@test.com", "홍길동"));

        // when
        Optional<User> found = repository.findByEmail("a@test.com");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_userType_USER만_조회되는지_확인() {
        // given
        User user = User.createPersonal("a@test.com", "홍길동");
        entityManager.persistAndFlush(user);

        // when
        Optional<User> found = repository.findByEmail("a@test.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUserType()).isEqualTo(UserType.USER);
    }

    @Test
    void existsByEmail_존재하면_true() {
        // given
        entityManager.persistAndFlush(User.createPersonal("a@test.com", "홍길동"));

        // when
        boolean exists = repository.existsByEmail("a@test.com");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_없으면_false() {
        // given

        // when
        boolean exists = repository.existsByEmail("none@test.com");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    void email_unique_constraint_중복_저장_시_예외() {
        // given
        repository.saveAndFlush(User.createPersonal("a@test.com", "홍길동"));
        User duplicate = User.createPersonal("a@test.com", "김철수");

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void email_null_저장_시_예외() {
        // given
        User user = User.createPersonal(null, "홍길동");

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_BaseTimeEntity_createdAt_updatedAt_자동_설정() {
        // given
        User user = User.createPersonal("a@test.com", "홍길동");

        // when
        User saved = repository.saveAndFlush(user);

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
