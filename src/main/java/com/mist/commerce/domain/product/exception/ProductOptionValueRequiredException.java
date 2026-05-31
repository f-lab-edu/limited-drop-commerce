package com.mist.commerce.domain.product.exception;

import static com.mist.commerce.domain.product.exception.ProductExceptionMessage.OPTION_VALUE_REQUIRED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ProductOptionValueRequiredException extends BusinessException {

    public static final String CODE = "PRODUCT_OPTION_VALUE_REQUIRED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public ProductOptionValueRequiredException(String optionGroupName) {
        super(CODE, HTTP_STATUS, OPTION_VALUE_REQUIRED.getMessage(),
                ErrorDetail.of("optionGroupName", optionGroupName, null));
    }
}
