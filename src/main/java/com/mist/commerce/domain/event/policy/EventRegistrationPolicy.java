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
import com.mist.commerce.domain.user.entity.UserType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventRegistrationPolicy {

    private final BrandRepository brandRepository;
    private final Clock clock;
    private final ProductRepository productRepository;

    public void validate(User user, EventCreateRequest request) {
        if (user.getUserType() != UserType.COMPANY) {
            throw new EventRegistrationForbiddenException(user.getId());
        }

        Company company = user.getCompany();
        if (company == null) {
            throw new EventRegistrationForbiddenException(user.getId());
        }

        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new BrandNotFoundException(user.getId()));

        if (!brand.getCompanyId().equals(company.getId())) {
            throw new EventRegistrationForbiddenException(user.getId());
        }

        List<Long> productIds = request.items().stream()
                .map(Item::productId)
                .distinct()
                .toList();

        List<Long> foundProductIds = productRepository.findAllById(productIds).stream()
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
            throw new EventScheduleValidationException("startAt must be in the future");
        }
    }
}
