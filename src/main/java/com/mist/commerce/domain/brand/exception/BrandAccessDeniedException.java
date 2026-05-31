package com.mist.commerce.domain.brand.exception;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class BrandAccessDeniedException extends BusinessException {

    public static final String CODE = "PRODUCT_REGISTRATION_FORBIDDEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public BrandAccessDeniedException(Long brandId,  Long userId) {
        super(CODE, HTTP_STATUS, "해당 브랜드를 관리할 수 있는 회사 소속 직원이 아닙니다.", ErrorDetail.of("brandId", brandId, "userId : " + userId));
    }
}
