package com.mist.commerce.domain.product.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandAccessDeniedException;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.exception.UserNotFoundException;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductRegistrationPolicyTest {

    private static final Long USER_ID = 10L;
    private static final Long BRAND_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private User user;

    private ProductRegistrationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProductRegistrationPolicy(userRepository, brandRepository);
    }

    @Test
    @DisplayName("TC-PRP-01 같은 회사 사용자는 상품 등록 권한 검증을 통과한다")
    void validate_whenUserAndBrandBelongToSameCompany_doesNotThrow() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(user.getCompanyId()).willReturn(1L);
        given(brandRepository.findById(BRAND_ID)).willReturn(Optional.of(brand(1L)));

        // When & Then
        assertThatCode(() -> policy.validate(USER_ID, request(BRAND_ID)))
                .doesNotThrowAnyException();
        then(userRepository).should().findById(USER_ID);
        then(brandRepository).should().findById(BRAND_ID);
    }

    @Test
    @DisplayName("TC-PRP-02 다른 회사 사용자는 403 권한 거부 예외가 발생한다")
    void validate_whenUserAndBrandBelongToDifferentCompanies_throwsBrandAccessDeniedException() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(user.getCompanyId()).willReturn(1L);
        given(brandRepository.findById(BRAND_ID)).willReturn(Optional.of(brand(99L)));

        // When & Then
        assertThatThrownBy(() -> policy.validate(USER_ID, request(BRAND_ID)))
                .isInstanceOf(BrandAccessDeniedException.class)
                .satisfies(exception -> {
                    BrandAccessDeniedException businessException = (BrandAccessDeniedException) exception;
                    assertThat(businessException.getCode()).isEqualTo("PRODUCT_REGISTRATION_FORBIDDEN");
                    assertThat(businessException.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(businessException.getErrorDetail().field()).isEqualTo("brandId");
                    assertThat(businessException.getErrorDetail().value()).isEqualTo(BRAND_ID);
                    assertThat(businessException.getErrorDetail().reason()).contains(String.valueOf(USER_ID));
                });
        then(userRepository).should().findById(USER_ID);
        then(brandRepository).should().findById(BRAND_ID);
    }

    @Test
    @DisplayName("TC-PRP-03 사용자가 없으면 USER_NOT_FOUND 예외가 발생하고 브랜드는 조회하지 않는다")
    void validate_whenUserDoesNotExist_throwsUserNotFoundException() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policy.validate(USER_ID, request(BRAND_ID)))
                .isInstanceOf(UserNotFoundException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");
        then(userRepository).should().findById(USER_ID);
        then(brandRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("TC-PRP-04 브랜드가 없으면 BRAND_NOT_FOUND 예외가 발생한다")
    void validate_whenBrandDoesNotExist_throwsBrandNotFoundException() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(brandRepository.findById(BRAND_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policy.validate(USER_ID, request(BRAND_ID)))
                .isInstanceOf(BrandNotFoundException.class)
                .extracting("code")
                .isEqualTo("BRAND_NOT_FOUND");
        then(userRepository).should().findById(USER_ID);
        then(brandRepository).should().findById(BRAND_ID);
    }

    @Test
    @DisplayName("TC-PRP-05 사용자와 브랜드가 모두 없으면 사용자 없음 예외가 먼저 발생한다")
    void validate_whenUserAndBrandDoNotExist_throwsUserNotFoundExceptionFirst() {
        // Given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policy.validate(USER_ID, request(BRAND_ID)))
                .isInstanceOf(UserNotFoundException.class)
                .extracting("code")
                .isEqualTo("USER_NOT_FOUND");
        then(userRepository).should().findById(USER_ID);
        then(brandRepository).shouldHaveNoInteractions();
    }

    private CreateProductRequest request(Long brandId) {
        return new CreateProductRequest(brandId, "name", "desc", 1_000L, ProductStatus.READY, null);
    }

    private Brand brand(Long companyId) {
        Brand brand = Brand.create(companyId, "brand", "description");
        ReflectionTestUtils.setField(brand, "id", BRAND_ID);
        return brand;
    }
}
