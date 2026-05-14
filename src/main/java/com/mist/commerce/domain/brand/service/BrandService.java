package com.mist.commerce.domain.brand.service;

import com.mist.commerce.domain.brand.dto.BrandCreateRequest;
import com.mist.commerce.domain.brand.dto.BrandCreateResponse;
import com.mist.commerce.domain.brand.entity.Brand;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BrandService {

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final Clock clock;

    public BrandCreateResponse create(Long userId, BrandCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BrandRegistrationForbiddenException(userId));

        if (user.getUserType() != UserType.COMPANY) {
            throw new BrandRegistrationForbiddenException(userId);
        }

        Company company = user.getCompany();
        if (company == null) {
            throw new BrandRegistrationForbiddenException(userId);
        }

        Long companyId = company.getId();
        if (brandRepository.existsByCompanyIdAndName(companyId, request.name())) {
            throw new BrandNameDuplicatedException(companyId, request.name());
        }

        Brand saved = brandRepository.save(Brand.create(companyId, request.name(), request.description()));
        return new BrandCreateResponse(
                saved.getId(),
                saved.getName(),
                saved.getDescription(),
                saved.getCompanyId(),
                clock.instant()
        );
    }
}
