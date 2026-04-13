package com.mist.commerce.domain.product.repository;

import com.mist.commerce.support.MySqlContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void findAll() {
        productRepository.findAll();
    }

}