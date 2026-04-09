package com.mist.commerce;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.mist.commerce.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockitoTest {

    @Mock
    ProductRepository repo;

    @Test
    void mockWorks() {
        assertThat(repo).isNotNull();
    }
}
