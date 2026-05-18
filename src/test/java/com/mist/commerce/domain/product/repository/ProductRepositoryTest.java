package com.mist.commerce.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ProductRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("상품을 저장하면 ID와 생성 수정 시간이 기록된다")
    void save_withValidProduct_assignsIdAndAuditFields() {
        Long brandId = persistBrand();
        Product product = Product.create(brandId, 1L,"Limited Sneakers", "2026 한정판", 150_000L, ProductStatus.READY);

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 브랜드 ID로 상품을 저장하면 FK 위반이 발생한다")
    void save_whenBrandIdDoesNotExist_throwsDataIntegrityViolationException() {
        Product product = Product.create(999_999L, 1L, "Limited Sneakers", "2026 한정판", 150_000L, ProductStatus.READY);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상품 상태 enum은 문자열로 저장되고 조회된다")
    void findById_afterSave_preservesStatusAsEnumString() {
        Long brandId = persistBrand();
        Product product = Product.create(brandId, 1L,"Limited Sneakers", "2026 한정판", 150_000L, ProductStatus.READY);
        Product saved = productRepository.saveAndFlush(product);
        entityManager.clear();

        Product found = productRepository.findById(saved.getId()).orElseThrow();
        String storedStatus = jdbcTemplate.queryForObject(
                "select status from product where id = ?",
                String.class,
                saved.getId()
        );

        assertThat(found.getStatus()).isEqualTo(ProductStatus.READY);
        assertThat(storedStatus).isEqualTo("READY");
    }

    private Long persistBrand() {
        // Brand 최소 스텁이 아직 없으므로 Implementer가 추가할 brand 테이블 계약만 native SQL로 고정한다.
        jdbcTemplate.update("insert into brand (company_id, name) values (?, ?)", 1L, "Mist");
        return jdbcTemplate.queryForObject("select id from brand where name = ?", Long.class, "Mist");
    }
}
