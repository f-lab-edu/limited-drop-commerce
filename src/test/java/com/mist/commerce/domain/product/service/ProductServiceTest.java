package com.mist.commerce.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductOptionGroup;
import com.mist.commerce.domain.product.entity.ProductOptionValue;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.domain.product.exception.ProductOptionGroupNameDuplicatedException;
import com.mist.commerce.domain.product.exception.ProductOptionValueDuplicatedException;
import com.mist.commerce.domain.product.exception.ProductOptionValueRequiredException;
import com.mist.commerce.domain.product.policy.ProductRegistrationPolicy;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.global.exception.BusinessException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long BRAND_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRegistrationPolicy productRegistrationPolicy;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productRegistrationPolicy);
    }

    @Test
    @DisplayName("옵션 그룹과 값을 포함한 상품 등록 요청은 상품 ID와 옵션 그룹 ID 목록을 반환한다")
    void createProduct_withOptionGroups_returnsProductIdAndOptionGroupIds() {
        CreateProductRequest request = validRequest(
                BRAND_ID,
                ProductStatus.READY,
                "2026 한정판",
                List.of(
                        optionGroup("색상", 0, true, List.of("Black", "White")),
                        optionGroup("사이즈", 1, true, List.of("260", "270"))));
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 100L);
            List<ProductOptionGroup> optionGroups = product.getOptionGroups();
            for (int i = 0; i < optionGroups.size(); i++) {
                ReflectionTestUtils.setField(optionGroups.get(i), "id", 1_000L + i);
            }
            return product;
        });

        CreateProductResponse response = productService.createProduct(USER_ID, request);

        assertThat(response.productId()).isEqualTo(100L);
        assertThat(response.optionGroupIds()).containsExactly(1_000L, 1_001L);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product product = productCaptor.getValue();
        assertThat(product.getOptionGroups()).hasSize(2);
        assertThat(product.getOptionGroups())
                .flatExtracting(ProductOptionGroup::getOptionValues)
                .extracting(ProductOptionValue::getValue)
                .containsExactly("Black", "White", "260", "270");
    }

    @Test
    @DisplayName("옵션 없는 상품 등록 요청은 빈 옵션 그룹 ID 목록을 반환한다")
    void createProduct_withoutOptionGroups_returnsProductIdAndEmptyOptionGroupIds() {
        CreateProductRequest request = validRequest(BRAND_ID, ProductStatus.READY, "2026 한정판");
        Product savedProduct = Product.create(BRAND_ID, USER_ID, request.name(), request.description(), request.price(), request.status());
        ReflectionTestUtils.setField(savedProduct, "id", 100L);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        CreateProductResponse response = productService.createProduct(USER_ID, request);

        assertThat(response.productId()).isEqualTo(100L);
        assertThat(response.optionGroupIds()).isEmpty();
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product product = productCaptor.getValue();
        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(request.name());
        assertThat(product.getDescription()).isEqualTo(request.description());
        assertThat(product.getPrice()).isEqualTo(request.price());
        assertThat(product.getStatus()).isEqualTo(request.status());
        assertThat(product.getOptionGroups()).isEmpty();
    }

    @Test
    @DisplayName("같은 요청 내 옵션 그룹명이 중복되면 409 비즈니스 예외가 발생하고 저장하지 않는다")
    void createProduct_whenOptionGroupNameDuplicated_throwsProductOptionGroupNameDuplicatedException() {
        CreateProductRequest request = validRequest(
                BRAND_ID,
                ProductStatus.READY,
                "2026 한정판",
                List.of(
                        optionGroup("색상", 0, true, List.of("Black")),
                        optionGroup("색상", 1, true, List.of("White"))));

        assertThatThrownBy(() -> productService.createProduct(USER_ID, request))
                .isInstanceOf(ProductOptionGroupNameDuplicatedException.class)
                .satisfies(exception -> {
                    ProductOptionGroupNameDuplicatedException businessException =
                            (ProductOptionGroupNameDuplicatedException) exception;
                    assertThat(businessException.getCode()).isEqualTo("PRODUCT_OPTION_GROUP_NAME_DUPLICATED");
                    assertThat(businessException.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("같은 그룹 내 옵션 값이 중복되면 409 비즈니스 예외가 발생하고 저장하지 않는다")
    void createProduct_whenOptionValueDuplicated_throwsProductOptionValueDuplicatedException() {
        CreateProductRequest request = validRequest(
                BRAND_ID,
                ProductStatus.READY,
                "2026 한정판",
                List.of(optionGroup("색상", 0, true, List.of("Black", "Black"))));

        assertThatThrownBy(() -> productService.createProduct(USER_ID, request))
                .isInstanceOf(ProductOptionValueDuplicatedException.class)
                .satisfies(exception -> {
                    ProductOptionValueDuplicatedException businessException =
                            (ProductOptionValueDuplicatedException) exception;
                    assertThat(businessException.getCode()).isEqualTo("PRODUCT_OPTION_VALUE_DUPLICATED");
                    assertThat(businessException.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("옵션 그룹 값이 0개이면 400 비즈니스 예외가 발생하고 저장하지 않는다")
    void createProduct_whenOptionValuesAreEmpty_throwsProductOptionValueRequiredException() {
        CreateProductRequest request = validRequest(
                BRAND_ID,
                ProductStatus.READY,
                "2026 한정판",
                List.of(optionGroup("색상", 0, true, List.of())));

        assertThatThrownBy(() -> productService.createProduct(USER_ID, request))
                .isInstanceOf(ProductOptionValueRequiredException.class)
                .satisfies(exception -> {
                    ProductOptionValueRequiredException businessException =
                            (ProductOptionValueRequiredException) exception;
                    assertThat(businessException.getCode()).isEqualTo("PRODUCT_OPTION_VALUE_REQUIRED");
                    assertThat(businessException.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("저장 중 DB 유니크 제약 위반이 발생하면 409 비즈니스 예외로 변환한다")
    void createProduct_whenDataIntegrityViolationOccurs_throwsProductOptionGroupNameDuplicatedException() {
        CreateProductRequest request = validRequest(
                BRAND_ID,
                ProductStatus.READY,
                "2026 한정판",
                List.of(optionGroup("색상", 0, true, List.of("Black"))));
        given(productRepository.save(any(Product.class)))
                .willThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException("Duplicate entry '1-색상' for key 'uk_pog_product_id_name'")));

        assertThatThrownBy(() -> productService.createProduct(USER_ID, request))
                .isInstanceOf(ProductOptionGroupNameDuplicatedException.class)
                .isInstanceOf(BusinessException.class)
                .extracting("httpStatus")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 ID로 상품 등록을 요청하면 BrandNotFoundException이 발생한다")
    void createProduct_whenBrandDoesNotExist_throwsBrandNotFoundException() {
        Long missingBrandId = 999_999L;
        CreateProductRequest request = validRequest(missingBrandId, ProductStatus.READY, "2026 한정판");
        willThrow(new BrandNotFoundException(missingBrandId))
                .given(productRegistrationPolicy).validate(eq(USER_ID), any(CreateProductRequest.class));

        assertThatThrownBy(() -> productService.createProduct(USER_ID, request))
                .isInstanceOf(BrandNotFoundException.class)
                .extracting("code")
                .isEqualTo("BRAND_NOT_FOUND");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("상품 등록 서비스는 트랜잭션 경계 안에서 실행된다")
    void createProduct_whenCalled_runsWithinTransaction() throws NoSuchMethodException {
        Method method = ProductService.class.getMethod("createProduct", Long.class, CreateProductRequest.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private CreateProductRequest validRequest(Long brandId, ProductStatus status, String description) {
        return new CreateProductRequest(brandId, "Limited Sneakers", description, 150_000L, status, null);
    }

    private CreateProductRequest validRequest(
            Long brandId,
            ProductStatus status,
            String description,
            List<CreateProductRequest.OptionGroupRequest> optionGroups
    ) {
        return new CreateProductRequest(brandId, "Limited Sneakers", description, 150_000L, status, optionGroups);
    }

    private CreateProductRequest.OptionGroupRequest optionGroup(
            String name,
            Integer displayOrder,
            Boolean required,
            List<String> values
    ) {
        return new CreateProductRequest.OptionGroupRequest(
                name,
                displayOrder,
                required,
                values.stream()
                        .map(CreateProductRequest.OptionValueRequest::new)
                        .toList());
    }
}
