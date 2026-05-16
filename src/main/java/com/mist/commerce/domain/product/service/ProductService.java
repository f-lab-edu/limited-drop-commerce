package com.mist.commerce.domain.product.service;

import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public CreateProductResponse createProduct(Long userId, CreateProductRequest request) {
        if (!brandRepository.existsById(request.brandId())) {
            throw new BrandNotFoundException(request.brandId());
        }

        Product product = Product.create(
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.status()
        );
        Product saved = productRepository.save(product);
        return new CreateProductResponse(saved.getId());
    }
}
