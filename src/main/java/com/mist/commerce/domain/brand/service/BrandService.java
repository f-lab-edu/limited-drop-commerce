package com.mist.commerce.domain.brand.service;

import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.policy.BrandRegistrationPolicy;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BrandService {

    private static final String BRAND_NAME_UNIQUE_CONSTRAINT = "uk_brand_company_id_name";

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final BrandRegistrationPolicy brandRegistrationPolicy;

    public BrandCreateResponse create(Long userId, BrandCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BrandRegistrationForbiddenException(userId));

        Long companyId = brandRegistrationPolicy.resolveTargetCompanyId(user, userId);
        brandRegistrationPolicy.ensureNameAvailable(companyId, request.name());

        Brand saved = persistBrand(companyId, request);

        return new BrandCreateResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                saved.getCompanyId(),
                saved.getCreatedAt()
        );
    }

    private Brand persistBrand(Long companyId, BrandCreateRequest request) {
        try {
            return brandRepository.save(Brand.create(companyId, request.name(), request.description()));
        } catch (DataIntegrityViolationException exception) {
            if (isBrandNameUniqueViolation(exception)) {
                throw new BrandNameDuplicatedException(companyId, request.name());
            }
            throw exception;
        }
    }

    private boolean isBrandNameUniqueViolation(DataIntegrityViolationException exception) {
        String message = exception.getMessage();
        return message != null && message.contains(BRAND_NAME_UNIQUE_CONSTRAINT);
    }
}
