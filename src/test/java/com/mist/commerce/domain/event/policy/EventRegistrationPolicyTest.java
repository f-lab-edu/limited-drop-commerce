package com.mist.commerce.domain.event.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.exception.EventRegistrationForbiddenException;
import com.mist.commerce.domain.event.exception.EventScheduleValidationException;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.domain.product.exception.ProductNotFoundException;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventRegistrationPolicyTest {

    private static final Instant START_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant PAST = Instant.parse("2025-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private User user;

    @Mock
    private Company company;

    private EventRegistrationPolicy policy;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        policy = new EventRegistrationPolicy(brandRepository, productRepository, fixedClock);
    }

    @Test
    @DisplayName("브랜드가 존재하지 않으면 BrandNotFoundException을 던진다")
    void validate_whenBrandDoesNotExist_throwsBrandNotFound() {
        givenCompanyUser(7L);
        given(brandRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policy.validate(user, request()))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    @DisplayName("브랜드가 다른 회사 소유이면 EventRegistrationForbiddenException을 던진다")
    void validate_whenBrandBelongsToOtherCompany_throwsEventRegistrationForbidden() {
        givenCompanyUser(7L);
        given(brandRepository.findById(1L)).willReturn(Optional.of(Brand.create(8L, "Mist", "desc")));

        assertThatThrownBy(() -> policy.validate(user, request()))
                .isInstanceOf(EventRegistrationForbiddenException.class);
    }

    @Test
    @DisplayName("items 중 하나라도 상품이 존재하지 않으면 ProductNotFoundException을 던진다")
    void validate_whenAnyProductDoesNotExist_throwsProductNotFound() {
        givenValidAuthority();
        given(productRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(product(10L)));

        EventCreateRequest request = request(List.of(item(10L), item(11L)), START_AT, END_AT);

        assertThatThrownBy(() -> policy.validate(user, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("startAt이 현재 시각보다 과거이면 EventScheduleValidationException을 던진다")
    void validate_whenStartAtIsBeforeNow_throwsEventScheduleValidationException() {
        givenValidAuthority();

        assertThatThrownBy(() -> policy.validate(user, request(List.of(item(10L)), PAST, END_AT)))
                .isInstanceOf(EventScheduleValidationException.class);
    }

    @Test
    @DisplayName("startAt이 현재 시각과 같으면 EventScheduleValidationException을 던진다")
    void validate_whenStartAtEqualsNow_throwsEventScheduleValidationException() {
        givenValidAuthority();

        assertThatThrownBy(() -> policy.validate(user, request(List.of(item(10L)), NOW, END_AT)))
                .isInstanceOf(EventScheduleValidationException.class);
    }

    @Test
    @DisplayName("모든 상품이 request.brandId와 같은 brandId 소속이면 통과한다")
    void validate_whenAllProductsBelongToRequestBrand_doesNotThrow() {
        givenCompanyUser(7L);
        given(brandRepository.findById(1L)).willReturn(Optional.of(Brand.create(7L, "Mist", "desc")));
        given(productRepository.findAllByIdInAndBrandId(List.of(10L, 11L), 1L))
                .willReturn(List.of(product(10L, 1L), product(11L, 1L)));

        EventCreateRequest request = request(List.of(item(10L), item(11L)), START_AT, END_AT);

        assertThatCode(() -> policy.validate(user, request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일부 상품의 brandId가 request.brandId와 다르면 ProductNotFoundException을 던진다")
    void validate_whenAnyProductBelongsToDifferentBrand_throwsEventRegistrationForbidden() {
        givenCompanyUser(7L);

        given(brandRepository.findById(1L)).willReturn(Optional.of(Brand.create(7L, "Mist", "desc")));
        given(productRepository.findAllByIdInAndBrandId(List.of(10L, 11L), 1L))
                .willReturn(List.of(product(10L, 1L)));

        EventCreateRequest request = request(List.of(item(10L), item(11L)), START_AT, END_AT);

        assertThatThrownBy(() -> policy.validate(user, request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("기업이 브랜드를 소유하고 상품과 일정이 유효하면 예외 없이 통과한다")
    void validate_whenCompanyOwnsBrandProductsExistAndScheduleValid_doesNotThrow() {
        givenValidAuthority();

        assertThatCode(() -> policy.validate(user, request()))
                .doesNotThrowAnyException();
    }

    private void givenCompanyUser(Long companyId) {
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(company);
        given(company.getId()).willReturn(companyId);
    }

    private void givenCompanyUserWithoutCompany() {
        given(user.getUserType()).willReturn(UserType.COMPANY);
        given(user.getCompany()).willReturn(null);
    }

    private void givenValidAuthority() {
        givenCompanyUser(7L);
        given(brandRepository.findById(1L)).willReturn(Optional.of(Brand.create(7L, "Mist", "desc")));
        given(productRepository.findAllByIdInAndBrandId(List.of(10L), 1L)).willReturn(List.of(product(10L, 1L)));
    }

    private EventCreateRequest request() {
        return request(List.of(item(10L)), START_AT, END_AT);
    }

    private EventCreateRequest request(
            List<EventCreateRequest.Item> items,
            Instant startAt,
            Instant endAt
    ) {
        return new EventCreateRequest(1L, "한정 스니커즈 드롭", startAt, endAt, items);
    }

    private EventCreateRequest.Item item(Long productId) {
        return new EventCreateRequest.Item(productId, new BigDecimal("150000"), 100, 10, List.of(optionStock()));
    }

    private Product product(Long id) {
        return product(id, 1L);
    }

    private Product product(Long id, Long brandId) {
        Product product = Product.create(brandId, 1L, "Sneakers", "desc", 150000L, ProductStatus.READY);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private EventCreateRequest.OptionStock optionStock() {
        return new EventCreateRequest.OptionStock(3L, 5L, 40);
    }
}
