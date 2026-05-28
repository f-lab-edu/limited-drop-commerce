package com.mist.commerce.domain.product.exception;

import static com.mist.commerce.domain.product.exception.ProductExceptionMessage.OPTION_GROUP_NAME_DUPLICATED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ProductOptionGroupNameDuplicatedException extends BusinessException {

    public static final String CODE = "PRODUCT_OPTION_GROUP_NAME_DUPLICATED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public ProductOptionGroupNameDuplicatedException(String name) {
        super(CODE, HTTP_STATUS, OPTION_GROUP_NAME_DUPLICATED.getMessage(), ErrorDetail.of("name", name, null));
    }

    public ProductOptionGroupNameDuplicatedException(String name, Throwable cause) {
        super(CODE, HTTP_STATUS, OPTION_GROUP_NAME_DUPLICATED.getMessage(), ErrorDetail.of("name", name, null), cause);
    }
}
