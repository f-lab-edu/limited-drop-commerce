package com.mist.commerce.domain.product.service;

import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateProductResponse createProduct(Long userId, CreateProductRequest request) {
        if (!brandRepository.existsById(request.brandId())) {
            throw new BrandNotFoundException(request.brandId());
        }

        // TODO: 도메인 규칙 추가 필요 : 해당 브랜드의 회사 소속 직원 검증


        Product product = Product.create(
                request.brandId(),
                userId,
                request.name(),
                request.description(),
                request.price(),
                request.status()
        );

        Product saved = productRepository.save(product);
        return CreateProductResponse.builder()
                .productId(saved.getId())
                .build();
    }
}
