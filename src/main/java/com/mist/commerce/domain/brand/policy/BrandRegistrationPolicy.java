package com.mist.commerce.domain.brand.policy;

import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.domain.brand.repository.BrandRepository;
import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrandRegistrationPolicy {

    private final BrandRepository brandRepository;

    public Long resolveTargetCompanyId(User user, Long userId) {
        if (user.getUserType() != UserType.COMPANY) {
            throw new BrandRegistrationForbiddenException(userId);
        }

        Company company = user.getCompany();
        if (company == null) {
            throw new BrandRegistrationForbiddenException(userId);
        }

        return company.getId();
    }

    public void ensureNameAvailable(Long companyId, String name) {
        if (brandRepository.existsByCompanyIdAndName(companyId, name)) {
            throw new BrandNameDuplicatedException(companyId, name);
        }
    }
}
