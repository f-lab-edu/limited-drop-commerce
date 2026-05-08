package com.mist.commerce.domain.user.service;

import com.mist.commerce.domain.user.entity.OAuthAccount;
import com.mist.commerce.domain.user.entity.OAuthProvider;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.domain.user.repository.OAuthAccountRepository;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Qualifier("defaultOAuth2UserService")
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String sub = attributeAsString(attributes, "sub");
        String email = attributeAsString(attributes, "email");
        String name = attributeAsString(attributes, "name");
        if (sub == null || email == null || name == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
        }

        User user;
        boolean isNewUser;

        var account = oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, sub);
        if (account.isPresent()) {
            user = account.get().getUser();
            isNewUser = false;
        } else {
            var userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                if (userByEmail.get().getUserType() == UserType.COMPANY) {
                    throw new OAuthAccountAlreadyLinkedToBusinessException();
                }
                throw new UserEmailDuplicatedException(email);
            }

            user = userRepository.save(User.createPersonal(email, name));
            oAuthAccountRepository.save(OAuthAccount.of(user, OAuthProvider.GOOGLE, sub, email));
            isNewUser = true;
        }

        Map<String, Object> mergedAttributes = new HashMap<>(attributes);
        mergedAttributes.put("userId", user.getId());
        mergedAttributes.put("userType", user.getUserType().name());
        mergedAttributes.put("status", user.getStatus().name());
        mergedAttributes.put("isNewUser", isNewUser);
        return new DefaultOAuth2User(oAuth2User.getAuthorities(), mergedAttributes, "sub");
    }

    private String attributeAsString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
