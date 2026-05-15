package com.mist.commerce.domain.brand.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrandRegistrationPolicyTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private User user;

    @Mock
    private Company company;

    private BrandRegistrationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BrandRegistrationPolicy(brandRepository);
    }

    @Test
    @DisplayName("기업 회원이 소속 회사를 가지면 회사 ID를 반환한다")
    void resolveTargetCompanyId_whenCompanyUserHasCompany_returnsCompanyId() {
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(company);
        given(company.getId()).willReturn(7L);

        Long companyId = policy.resolveTargetCompanyId(user, 1L);

        assertThat(companyId).isEqualTo(7L);
    }

    @Test
    @DisplayName("기업 회원이 아니면 BrandRegistrationForbiddenException을 던진다")
    void resolveTargetCompanyId_whenUserIsNotCompanyType_throwsBrandRegistrationForbidden() {
        given(user.getUserType()).willReturn(UserType.USER);

        assertThatThrownBy(() -> policy.resolveTargetCompanyId(user, 1L))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
    }

    @Test
    @DisplayName("기업 회원이지만 소속 회사가 없으면 BrandRegistrationForbiddenException을 던진다")
    void resolveTargetCompanyId_whenCompanyUserHasNoCompany_throwsBrandRegistrationForbidden() {
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(null);

        assertThatThrownBy(() -> policy.resolveTargetCompanyId(user, 1L))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
    }

    @Test
    @DisplayName("같은 회사에 동일 브랜드명이 없으면 통과한다")
    void ensureNameAvailable_whenNameNotUsed_doesNotThrow() {
        given(brandRepository.existsByCompanyIdAndName(7L, "Mist")).willReturn(false);

        assertThatCode(() -> policy.ensureNameAvailable(7L, "Mist"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 회사에 동일 브랜드명이 있으면 BrandNameDuplicatedException을 던진다")
    void ensureNameAvailable_whenNameAlreadyExists_throwsBrandNameDuplicated() {
        given(brandRepository.existsByCompanyIdAndName(7L, "Mist")).willReturn(true);

        assertThatThrownBy(() -> policy.ensureNameAvailable(7L, "Mist"))
                .isInstanceOf(BrandNameDuplicatedException.class);
    }
}
