package com.mist.commerce.domain.user.repository;

import com.mist.commerce.domain.user.entity.OAuthAccount;
import com.mist.commerce.domain.user.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
