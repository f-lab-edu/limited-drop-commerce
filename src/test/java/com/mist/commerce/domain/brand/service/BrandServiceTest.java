package com.mist.commerce.domain.brand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.policy.BrandRegistrationPolicy;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private BrandRegistrationPolicy brandRegistrationPolicy;

    @Mock
    private User user;

    private BrandService service;

    @BeforeEach
    void setUp() {
        service = new BrandService(userRepository, brandRepository, brandRegistrationPolicy);
    }

    @Test
    @DisplayName("정책이 회사 ID를 해석하고 이름이 사용 가능하면 Brand를 저장하고 응답을 반환한다")
    void create_whenPolicyAllows_persistsBrandAndReturnsResponse() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        LocalDateTime savedCreatedAt = LocalDateTime.parse("2026-05-14T10:30:00");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(user, 1L)).willReturn(7L);
        Brand savedBrand = Brand.create(7L, "Mist", "desc");
        ReflectionTestUtils.setField(savedBrand, "id", 42L);
        ReflectionTestUtils.setField(savedBrand, "createdAt", savedCreatedAt);
        given(brandRepository.save(any(Brand.class))).willReturn(savedBrand);

        BrandCreateResponse response = service.create(1L, request);

        assertThat(response.brandId()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("Mist");
        assertThat(response.description()).isEqualTo("desc");
        assertThat(response.companyId()).isEqualTo(7L);
        assertThat(response.createdAt()).isEqualTo(savedCreatedAt);

        verify(brandRegistrationPolicy).ensureNameAvailable(7L, "Mist");
        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(brandCaptor.capture());
        Brand capturedBrand = brandCaptor.getValue();
        assertThat(capturedBrand.getId()).isNull();
        assertThat(capturedBrand.getCompanyId()).isEqualTo(7L);
        assertThat(capturedBrand.getName()).isEqualTo("Mist");
        assertThat(capturedBrand.getDescription()).isEqualTo("desc");
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 BrandRegistrationForbiddenException이 발생한다")
    void create_whenUserMissing_throwsBrandRegistrationForbidden() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    @DisplayName("정책이 등록 자격 미달을 보고하면 예외가 그대로 전파된다")
    void create_whenPolicyRejectsEligibility_propagatesForbiddenException() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(user, 1L))
                .willThrow(new BrandRegistrationForbiddenException(1L));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    @DisplayName("정책이 브랜드명 중복을 보고하면 예외가 그대로 전파된다")
    void create_whenPolicyRejectsName_propagatesDuplicatedException() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(user, 1L)).willReturn(7L);
        willThrow(new BrandNameDuplicatedException(7L, "Mist"))
                .given(brandRegistrationPolicy).ensureNameAvailable(7L, "Mist");

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandNameDuplicatedException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    @DisplayName("동시 중복 등록으로 저장 시 유니크 제약 위반이 발생하면 BrandNameDuplicatedException으로 변환된다")
    void create_whenUniqueConstraintViolationOccursDuringSave_throwsBrandNameDuplicated() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(user, 1L)).willReturn(7L);
        given(brandRepository.save(any(Brand.class)))
                .willThrow(new DataIntegrityViolationException("uk_brand_company_id_name"));

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandNameDuplicatedException.class);
    }

    @Test
    @DisplayName("Brand 이름 중복 제약이 아닌 저장 제약 위반은 원래 예외를 다시 던진다")
    void create_whenOtherDataIntegrityViolationOccursDuringSave_rethrowsOriginalException() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("other_constraint");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(user, 1L)).willReturn(7L);
        given(brandRepository.save(any(Brand.class))).willThrow(exception);

        assertThatThrownBy(() -> service.create(1L, request))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("Brand 등록 응답의 createdAt은 저장된 Brand의 생성일시와 일치한다")
    void create_returnsCreatedAtFromSavedBrand() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        LocalDateTime savedCreatedAt = LocalDateTime.parse("2026-05-14T10:30:00");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRegistrationPolicy.resolveTargetCompanyId(eq(user), eq(1L))).willReturn(7L);
        Brand savedBrand = Brand.create(7L, "Mist", "desc");
        ReflectionTestUtils.setField(savedBrand, "id", 42L);
        ReflectionTestUtils.setField(savedBrand, "createdAt", savedCreatedAt);
        given(brandRepository.save(any(Brand.class))).willReturn(savedBrand);

        BrandCreateResponse response = service.create(1L, request);

        assertThat(response.createdAt()).isEqualTo(savedCreatedAt);
    }
}
