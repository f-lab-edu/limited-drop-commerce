package com.mist.commerce.domain.product.exception;

import static com.mist.commerce.domain.product.exception.ProductExceptionMessage.OPTION_VALUE_DUPLICATED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ProductOptionValueDuplicatedException extends BusinessException {

    public static final String CODE = "PRODUCT_OPTION_VALUE_DUPLICATED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public ProductOptionValueDuplicatedException(String value) {
        super(CODE, HTTP_STATUS, OPTION_VALUE_DUPLICATED.getMessage(), ErrorDetail.of("value", value, null));
    }
}
