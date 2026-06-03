package com.mist.commerce.domain.event.policy;

import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNotFoundException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.dto.EventCreateRequest.Item;
import com.mist.commerce.domain.event.exception.EventRegistrationForbiddenException;
import com.mist.commerce.domain.event.exception.EventScheduleValidationException;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.exception.ProductNotFoundException;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventRegistrationPolicy {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public void  validate(User user, EventCreateRequest request) {
        Company company = user.getCompany();
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new BrandNotFoundException(user.getId()));

        if (!brand.getCompanyId().equals(company.getId())) {
            throw new EventRegistrationForbiddenException(user.getId());
        }

        List<Long> productIds = request.items().stream()
                .map(Item::productId)
                .distinct()
                .toList();

        List<Product> allByIdInAndBrandId = productRepository.findAllByIdInAndBrandId(productIds, request.brandId());
        List<Long> foundProductIds = allByIdInAndBrandId.stream()
                .map(Product::getId)
                .toList();

        productIds.stream()
                .filter(productId -> !foundProductIds.contains(productId))
                .findFirst()
                .ifPresent(productId -> {
                    throw new ProductNotFoundException(productId);
                });

        // 자기 값만 보고 판단할 수 없기 때문에 서비스 규칙
        Instant now = clock.instant();
        if (!request.startAt().isAfter(now)) {
            throw new EventScheduleValidationException();
        }
    }
}
