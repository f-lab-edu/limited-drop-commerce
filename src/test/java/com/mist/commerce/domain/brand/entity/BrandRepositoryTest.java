package com.mist.commerce.domain.brand.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.support.MySqlContainerTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class BrandRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("필수 입력값으로 Brand를 생성하면 영속화 전 식별자는 없고 입력값이 보존된다")
    void create_withValidInputs_createsTransientBrand() {
        Brand brand = Brand.create(1L, "Mist Brand", "Limited drop brand");

        assertThat(brand.getId()).isNull();
        assertThat(brand.getCompanyId()).isEqualTo(1L);
        assertThat(brand.getName()).isEqualTo("Mist Brand");
        assertThat(brand.getDescription()).isEqualTo("Limited drop brand");
    }

    @Test
    @DisplayName("description이 null이어도 Brand를 생성할 수 있다")
    void create_withNullDescription_createsBrand() {
        Brand brand = Brand.create(1L, "Mist Brand", null);

        assertThat(brand.getId()).isNull();
        assertThat(brand.getCompanyId()).isEqualTo(1L);
        assertThat(brand.getName()).isEqualTo("Mist Brand");
        assertThat(brand.getDescription()).isNull();
    }

    @Test
    @DisplayName("companyId가 null이면 영속화 전에 NullPointerException이 발생한다")
    void create_withNullCompanyId_throwsNullPointerExceptionBeforePersistence() {
        assertThatNullPointerException()
                .isThrownBy(() -> Brand.create(null, "Mist Brand", "Limited drop brand"));
    }

    @Test
    @DisplayName("name이 null이면 영속화 전에 NullPointerException이 발생한다")
    void create_withNullName_throwsNullPointerExceptionBeforePersistence() {
        assertThatNullPointerException()
                .isThrownBy(() -> Brand.create(1L, null, "Limited drop brand"));
    }

    @Test
    @DisplayName("Brand를 저장하면 DB 식별자가 생성되고 id로 다시 조회할 수 있다")
    void saveAndFlush_assignsGeneratedIdAndCanReloadById() {
        Long companyId = persistCompanyFixture(1L);
        Brand brand = Brand.create(companyId, "Mist Brand", "Limited drop brand");

        Brand saved = brandRepository.saveAndFlush(brand);
        entityManager.clear();

        Brand found = brandRepository.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCompanyId()).isEqualTo(companyId);
        assertThat(found.getName()).isEqualTo("Mist Brand");
        assertThat(found.getDescription()).isEqualTo("Limited drop brand");
    }

    @Test
    @DisplayName("영속성 식별성은 비즈니스 필드가 아니라 JPA id를 기준으로 확인한다")
    void identity_usesJpaIdWithoutRequiringObjectEqualityAcrossPersistenceContexts() {
        Long companyId = persistCompanyFixture(1L);
        Brand saved = brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", "Limited drop brand"));

        entityManager.clear();
        Brand firstLoad = brandRepository.findById(saved.getId()).orElseThrow();

        entityManager.clear();
        Brand secondLoad = brandRepository.findById(saved.getId()).orElseThrow();

        assertThat(firstLoad).isNotSameAs(secondLoad);
        assertThat(firstLoad.getId()).isEqualTo(secondLoad.getId());
    }

    @Test
    @DisplayName("Brand 저장 시 BaseTimeEntity의 생성일시와 수정일시가 채워진다")
    void saveAndFlush_populatesBaseTimeEntityAuditFields() {
        Long companyId = persistCompanyFixture(1L);
        Brand saved = brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", "Limited drop brand"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("name은 최대 길이 100자까지 저장할 수 있다")
    void saveAndFlush_acceptsNameAtMaxLength() {
        Long companyId = persistCompanyFixture(1L);
        String maxLengthName = "A".repeat(100);

        Brand saved = brandRepository.saveAndFlush(Brand.create(companyId, maxLengthName, null));
        entityManager.clear();

        Brand found = brandRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo(maxLengthName);
    }

    @Test
    @DisplayName("name이 100자를 초과하면 저장에 실패한다")
    void saveAndFlush_rejectsNameOverMaxLength() {
        Long companyId = persistCompanyFixture(1L);
        String overLimitName = "A".repeat(101);

        assertThatThrownBy(() -> brandRepository.saveAndFlush(Brand.create(companyId, overLimitName, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("description은 최대 길이 255자까지 저장할 수 있다")
    void saveAndFlush_acceptsDescriptionAtMaxLength() {
        Long companyId = persistCompanyFixture(1L);
        String maxLengthDescription = "A".repeat(255);

        Brand saved = brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", maxLengthDescription));
        entityManager.clear();

        Brand found = brandRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDescription()).isEqualTo(maxLengthDescription);
    }

    @Test
    @DisplayName("description이 255자를 초과하면 저장에 실패한다")
    void saveAndFlush_rejectsDescriptionOverMaxLength() {
        Long companyId = persistCompanyFixture(1L);
        String overLimitDescription = "A".repeat(256);

        assertThatThrownBy(() -> brandRepository.saveAndFlush(
                Brand.create(companyId, "Mist Brand", overLimitDescription)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("description은 null로 저장할 수 있다")
    void saveAndFlush_acceptsNullDescription() {
        Long companyId = persistCompanyFixture(1L);

        Brand saved = brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", null));
        entityManager.clear();

        Brand found = brandRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDescription()).isNull();
    }

    @Test
    @DisplayName("같은 회사 안에서 동일한 브랜드명은 중복 저장할 수 없다")
    void saveAndFlush_rejectsDuplicateNameWithinSameCompany() {
        Long companyId = persistCompanyFixture(1L);
        brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", "first"));

        assertThatThrownBy(() -> brandRepository.saveAndFlush(Brand.create(companyId, "Mist Brand", "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 회사는 같은 브랜드명을 각각 저장할 수 있다")
    void saveAndFlush_acceptsSameNameAcrossDifferentCompanies() {
        Long firstCompanyId = persistCompanyFixture(1L);
        Long secondCompanyId = persistCompanyFixture(2L);

        Brand first = brandRepository.saveAndFlush(Brand.create(firstCompanyId, "Mist Brand", "first"));
        Brand second = brandRepository.saveAndFlush(Brand.create(secondCompanyId, "Mist Brand", "second"));

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull();
        assertThat(first.getCompanyId()).isNotEqualTo(second.getCompanyId());
        assertThat(first.getName()).isEqualTo(second.getName());
    }

    @Test
    @DisplayName("존재하지 않는 companyId로 Brand를 저장하면 FK 제약으로 실패한다")
    void saveAndFlush_rejectsBrandWithNonexistentCompanyId() {
        assertThatThrownBy(() -> brandRepository.saveAndFlush(
                Brand.create(9999L, "Mist Brand", "Limited drop brand")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Brand 엔티티의 Java 필드명은 이후 DTO 매핑을 위해 camelCase를 유지한다")
    void entityFieldNames_areCamelCaseForLaterDtoMapping() throws NoSuchFieldException {
        assertThat(Brand.class.getDeclaredField("id")).isNotNull();
        assertThat(Brand.class.getDeclaredField("companyId")).isNotNull();
        assertThat(Brand.class.getDeclaredField("name")).isNotNull();
        assertThat(Brand.class.getDeclaredField("description")).isNotNull();

        assertThatThrownBy(() -> Brand.class.getDeclaredField("company_id"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    private Long persistCompanyFixture(Long id) {
        entityManager.createNativeQuery("insert into company (id) values (?)")
                .setParameter(1, id)
                .executeUpdate();
        entityManager.flush();
        return id;
    }

}
