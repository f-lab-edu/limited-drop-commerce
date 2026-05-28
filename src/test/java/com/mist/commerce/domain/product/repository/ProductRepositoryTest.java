package com.mist.commerce.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("옵션 그룹과 값을 포함한 상품을 저장하면 옵션이 cascade 저장되고 FK가 연결된다")
    void save_withOptionGroupsAndValues_cascadesAndConnectsForeignKeys() {
        Long brandId = persistBrand();
        Product product = Product.create(
                brandId,
                1L,
                "Limited Sneakers",
                "2026 한정판",
                150_000L,
                ProductStatus.READY,
                List.of(
                        new Product.OptionGroupSpec("색상", 0, true, List.of("Black", "White")),
                        new Product.OptionGroupSpec("사이즈", 1, true, List.of("260", "270"))));

        Product saved = productRepository.saveAndFlush(product);
        entityManager.clear();

        List<Map<String, Object>> groups = jdbcTemplate.queryForList(
                """
                        select id, name, display_order, case when required then 1 else 0 end as required_value
                        from product_option_group
                        where product_id = ?
                        order by display_order
                        """,
                saved.getId());
        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(row -> row.get("name")).containsExactly("색상", "사이즈");
        assertThat(groups).extracting(row -> ((Number) row.get("display_order")).intValue()).containsExactly(0, 1);
        assertThat(groups).extracting(row -> ((Number) row.get("required_value")).intValue()).containsExactly(1, 1);

        Long colorGroupId = ((Number) groups.get(0).get("id")).longValue();
        Long sizeGroupId = ((Number) groups.get(1).get("id")).longValue();

        Long valueCount = jdbcTemplate.queryForObject(
                "select count(*) from product_option_value where option_group_id in (?, ?)",
                Long.class,
                colorGroupId,
                sizeGroupId);
        assertThat(valueCount).isEqualTo(4L);

        List<String> colorValues = jdbcTemplate.queryForList(
                "select value from product_option_value where option_group_id = ? order by value",
                String.class,
                colorGroupId);
        List<String> sizeValues = jdbcTemplate.queryForList(
                "select value from product_option_value where option_group_id = ? order by value",
                String.class,
                sizeGroupId);
        assertThat(colorValues).containsExactly("Black", "White");
        assertThat(sizeValues).containsExactly("260", "270");
    }

    @Test
    @DisplayName("같은 상품 내 동일 옵션 그룹명을 직접 저장하면 유니크 제약 위반이 발생한다")
    void save_whenSameProductHasDuplicateOptionGroupName_throwsDataIntegrityViolationException() {
        Product saved = saveProductWithSingleOption("색상", "Black");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_option_group (product_id, name, display_order, required) values (?, ?, ?, ?)",
                saved.getId(),
                "색상",
                1,
                true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 그룹 내 동일 옵션 값을 직접 저장하면 유니크 제약 위반이 발생한다")
    void save_whenSameGroupHasDuplicateOptionValue_throwsDataIntegrityViolationException() {
        Product saved = saveProductWithSingleOption("색상", "Black");
        Long groupId = saved.getOptionGroups().get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into product_option_value (option_group_id, value) values (?, ?)",
                groupId,
                "Black"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 상품이면 같은 옵션 그룹명을 저장할 수 있다")
    void save_whenDifferentProductsHaveSameOptionGroupName_allowsSameName() {
        Product first = saveProductWithSingleOption("색상", "Black");
        Product second = Product.create(
                first.getBrandId(),
                2L,
                "Limited Hoodie",
                "2026 한정판",
                90_000L,
                ProductStatus.READY,
                List.of(new Product.OptionGroupSpec("색상", 0, true, List.of("White"))));

        Product savedSecond = productRepository.saveAndFlush(second);
        entityManager.clear();

        Long count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from product_option_group
                        where name = ? and product_id in (?, ?)
                        """,
                Long.class,
                "색상",
                first.getId(),
                savedSecond.getId());

        assertThat(count).isEqualTo(2L);
    }

    private Product saveProductWithSingleOption(String groupName, String value) {
        Long brandId = persistBrand();
        Product product = Product.create(
                brandId,
                1L,
                "Limited Sneakers",
                "2026 한정판",
                150_000L,
                ProductStatus.READY,
                List.of(new Product.OptionGroupSpec(groupName, 0, true, List.of(value))));

        return productRepository.saveAndFlush(product);
    }

    private Long persistBrand() {
        // Brand 최소 스텁이 아직 없으므로 Implementer가 추가할 brand 테이블 계약만 native SQL로 고정한다.
        jdbcTemplate.update("insert into brand (company_id, name) values (?, ?)", 1L, "Mist");
        return jdbcTemplate.queryForObject("select id from brand where name = ?", Long.class, "Mist");
    }
}
