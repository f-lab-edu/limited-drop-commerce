package com.mist.commerce.domain.brand.exception;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

public class BrandNotFoundException extends BusinessException {
    public static final String CODE = "BRAND_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public BrandNotFoundException(Long brandId) {
        super(CODE, HTTP_STATUS, "브랜드를 찾을 수 없습니다.", ErrorDetail.of("brandId", brandId, null));
    }
}
