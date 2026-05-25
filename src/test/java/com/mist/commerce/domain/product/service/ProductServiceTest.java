package com.mist.commerce.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long BRAND_ID = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private UserRepository userRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, brandRepository, userRepository);
    }

    @Test
    @DisplayName("존재하는 브랜드와 유효한 요청으로 상품을 등록하면 상품 ID를 반환한다")
    void createProduct_withValidRequest_returnsProductIdAndSavesOnce() {
        CreateProductRequest request = validRequest(BRAND_ID, ProductStatus.READY, "2026 한정판");
        Product savedProduct = Product.create(BRAND_ID, USER_ID, request.name(), request.description(), request.price(), request.status());
        ReflectionTestUtils.setField(savedProduct, "id", 100L);
        given(brandRepository.existsById(BRAND_ID)).willReturn(true);
        given(productRepository.save(any(Product.class))).willReturn(savedProduct);

        CreateProductResponse response = productService.createProduct(USER_ID, request);

        assertThat(response.productId()).isEqualTo(100L);
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product product = productCaptor.getValue();
        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(request.name());
        assertThat(product.getDescription()).isEqualTo(request.description());
        assertThat(product.getPrice()).isEqualTo(request.price());
        assertThat(product.getStatus()).isEqualTo(request.status());
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 ID로 상품 등록을 요청하면 BrandNotFoundException이 발생한다")
    void createProduct_whenBrandDoesNotExist_throwsBrandNotFoundException() {
        Long missingBrandId = 999_999L;
        CreateProductRequest request = validRequest(missingBrandId, ProductStatus.READY, "2026 한정판");
        given(brandRepository.existsById(missingBrandId)).willReturn(false);

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
        return new CreateProductRequest(brandId, "Limited Sneakers", description, 150_000L, status);
    }
}
