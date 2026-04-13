package com.mist.commerce.domain.product.repository;

import com.mist.commerce.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
