package com.mist.commerce;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.mist.commerce.domain.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class BeanInjectionTest {

    @Autowired
    ProductService productService;

    @Test
    void injected() {
        assertThat(productService).isNotNull();
    }
}
