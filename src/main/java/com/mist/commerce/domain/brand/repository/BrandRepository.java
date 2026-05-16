package com.mist.commerce.domain.brand.repository;

import com.mist.commerce.domain.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
