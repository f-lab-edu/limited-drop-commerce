package com.mist.commerce.domain.brand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-14T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private User user;

    @Mock
    private Company company;

    private BrandService service;

    // Constructor/API expected from production:
    // BrandService(UserRepository userRepository, BrandRepository brandRepository, Clock clock).
    // The response createdAt is expected to come from this Clock in the unit path because
    // a saved Brand returned by a mocked repository does not have JPA audit fields populated.
    @BeforeEach
    void setUp() {
        service = new BrandService(
                userRepository,
                brandRepository,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("기업 사용자가 유효한 입력으로 등록하면 Brand가 저장되고 응답이 반환된다")
    void create_withValidCompanyUserAndUniqueName_persistsBrandAndReturnsResponse() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        givenCompanyUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRepository.existsByCompanyIdAndName(1L, "Mist")).willReturn(false);
        Brand savedBrand = Brand.create(1L, "Mist", "desc");
        ReflectionTestUtils.setField(savedBrand, "id", 42L);
        given(brandRepository.save(any(Brand.class))).willReturn(savedBrand);

        BrandCreateResponse response = service.create(1L, request);

        assertThat(response.brandId()).isEqualTo(42L);
        assertThat(response.name()).isEqualTo("Mist");
        assertThat(response.description()).isEqualTo("desc");
        assertThat(response.companyId()).isEqualTo(1L);
        assertThat(response.createdAt()).isEqualTo(FIXED_NOW);

        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(brandCaptor.capture());
        Brand capturedBrand = brandCaptor.getValue();
        assertThat(capturedBrand.getId()).isNull();
        assertThat(capturedBrand.getCompanyId()).isEqualTo(1L);
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
    @DisplayName("기업 회원이 아닌 사용자가 요청하면 BrandRegistrationForbiddenException이 발생한다")
    void create_whenUserIsNotCompanyType_throwsBrandRegistrationForbidden() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getUserType()).willReturn(UserType.USER);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    @DisplayName("기업 회원이지만 소속 Company가 없으면 BrandRegistrationForbiddenException이 발생한다")
    void create_whenCompanyUserHasNoCompanyAssociation_throwsBrandRegistrationForbidden() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(null);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandRegistrationForbiddenException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    @DisplayName("동일 회사 내에 같은 이름의 Brand가 이미 있으면 BrandNameDuplicatedException이 발생한다")
    void create_whenBrandNameAlreadyExistsInCompany_throwsBrandNameDuplicated() {
        BrandCreateRequest request = new BrandCreateRequest("Mist", "desc");
        givenCompanyUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(brandRepository.existsByCompanyIdAndName(1L, "Mist")).willReturn(true);

        assertThatThrownBy(() -> service.create(1L, request))
                .isInstanceOf(BrandNameDuplicatedException.class);
        verify(brandRepository, never()).save(any(Brand.class));
    }

    private void givenCompanyUser(Long companyId) {
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(company);
        given(company.getId()).willReturn(companyId);
    }
}
