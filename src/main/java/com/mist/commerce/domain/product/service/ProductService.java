package com.mist.commerce.domain.product.service;

import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.product.dto.CreateProductResponse;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductOptionGroup;
import com.mist.commerce.domain.product.exception.ProductOptionGroupNameDuplicatedException;
import com.mist.commerce.domain.product.policy.ProductRegistrationPolicy;
import com.mist.commerce.domain.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductRegistrationPolicy productRegistrationPolicy;

    @Transactional
    public CreateProductResponse createProduct(Long userId, CreateProductRequest request) {
        productRegistrationPolicy.validate(userId, request);

        List<Product.OptionGroupSpec> optionGroupSpecs = request.optionGroups() == null
                ? List.of()
                : request.optionGroups().stream()
                .map(optionGroup -> Product.OptionGroupSpec.builder()
                        .name(optionGroup.name())
                        .displayOrder(optionGroup.displayOrder())
                        .required(optionGroup.required())
                        .values(optionGroup.values().stream()
                                .map(CreateProductRequest.OptionValueRequest::value)
                                .toList())
                        .build()
                ).toList();

        Product product = Product.create(
                request.brandId(),
                userId,
                request.name(),
                request.description(),
                request.price(),
                request.status(),
                optionGroupSpecs
        );

        try {
            Product saved = productRepository.save(product);
            return CreateProductResponse.builder()
                    .productId(saved.getId())
                    .optionGroupIds(saved.getOptionGroups().stream()
                            .map(ProductOptionGroup::getId)
                            .toList())
                    .build();
        } catch (DataIntegrityViolationException exception) {
            if (isOptionGroupNameDuplicate(exception)) {
                throw new ProductOptionGroupNameDuplicatedException(request.name(), exception);
            }
            throw exception;
        }
    }

    private boolean isOptionGroupNameDuplicate(DataIntegrityViolationException exception) {
        Throwable rootCause = exception.getRootCause();

        return rootCause != null
                && rootCause.getMessage() != null
                && rootCause.getMessage().contains("uk_pog_product_id_name");
    }
}
