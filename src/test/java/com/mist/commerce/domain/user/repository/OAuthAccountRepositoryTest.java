package com.mist.commerce.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import com.mist.commerce.domain.user.entity.OAuthAccount;
import com.mist.commerce.domain.user.entity.OAuthProvider;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.util.Optional;
import org.hibernate.Hibernate;
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
class OAuthAccountRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private OAuthAccountRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByProviderAndProviderUserId_존재하면_OAuthAccount_반환() {
        // given
        User user = savedUser("test@gmail.com");
        entityManager.persistAndFlush(OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-123", "test@gmail.com"));

        // when
        Optional<OAuthAccount> found = repository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getProviderUserId()).isEqualTo("sub-123");
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void findByProviderAndProviderUserId_없으면_empty() {
        // given

        // when
        Optional<OAuthAccount> found = repository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-999");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findByProviderAndProviderUserId_providerUserId_불일치하면_empty() {
        // given
        User user = savedUser("test@gmail.com");
        entityManager.persistAndFlush(OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-123", "test@gmail.com"));

        // when
        Optional<OAuthAccount> found = repository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-999");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void provider_providerUserId_unique_constraint_중복_저장_예외() {
        // given
        User user = savedUser("test@gmail.com");
        repository.saveAndFlush(OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-123", "test@gmail.com"));
        OAuthAccount duplicate = OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-123", "other@gmail.com");

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providerEmail_null_저장_허용() {
        // given
        User user = savedUser("test@gmail.com");
        OAuthAccount account = OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-456", null);

        // when
        OAuthAccount saved = repository.saveAndFlush(account);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProviderEmail()).isNull();
    }

    @Test
    void providerUserId_null_저장_시_예외() {
        // given
        User user = savedUser("test@gmail.com");
        OAuthAccount account = OAuthAccount.of(user, OAuthProvider.GOOGLE, null, "test@gmail.com");

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void User_FetchType_LAZY_연관관계_확인() {
        // given
        User user = savedUser("test@gmail.com");
        entityManager.persistAndFlush(OAuthAccount.of(user, OAuthProvider.GOOGLE, "sub-123", "test@gmail.com"));
        entityManager.clear();

        // when
        OAuthAccount found = repository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123").get();

        // then
        assertThat(Hibernate.isInitialized(found.getUser())).isFalse();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
        assertThat(found.getUser().getEmail()).isEqualTo("test@gmail.com");
        assertThat(Hibernate.isInitialized(found.getUser())).isTrue();
    }

    private User savedUser(String email) {
        return entityManager.persistAndFlush(User.createPersonal(email, "홍길동"));
    }
}
