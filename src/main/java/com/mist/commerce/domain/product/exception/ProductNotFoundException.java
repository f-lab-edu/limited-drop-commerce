package com.mist.commerce.domain.product.exception;

import static com.mist.commerce.domain.product.exception.ProductExceptionMessage.*;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends BusinessException {

    public static final String CODE = "PRODUCT_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.NOT_FOUND;

    public ProductNotFoundException(Long productId) {
        super(CODE, HTTP_STATUS, NOT_FOUND.getMessage(), ErrorDetail.of("productId", productId, null));
    }
}
