package com.mist.commerce.domain.product.repository;

import com.mist.commerce.domain.product.entity.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {
}
