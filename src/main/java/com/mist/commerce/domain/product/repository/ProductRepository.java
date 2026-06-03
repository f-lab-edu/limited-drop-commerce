package com.mist.commerce.domain.product.repository;

import com.mist.commerce.domain.product.entity.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByIdInAndBrandId(Collection<Long> ids, Long brandId);

}
