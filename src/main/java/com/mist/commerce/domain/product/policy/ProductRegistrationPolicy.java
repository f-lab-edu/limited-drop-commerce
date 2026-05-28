package com.mist.commerce.domain.product.policy;

import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.product.dto.CreateProductRequest;
import com.mist.commerce.domain.brand.exception.BrandAccessDeniedException;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.exception.UserNotFoundException;
import com.mist.commerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductRegistrationPolicy {

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;

    public void validate(Long userId, CreateProductRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new BrandNotFoundException(request.brandId()));

        if (brand.getCompanyId().equals(user.getCompanyId())) {
            throw new BrandAccessDeniedException(brand.getId(), userId);
        }
    }
}
