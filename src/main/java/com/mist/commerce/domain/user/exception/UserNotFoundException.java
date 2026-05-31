package com.mist.commerce.domain.user.exception;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {
    public static final String CODE = "USER_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.NOT_FOUND;

    public UserNotFoundException(Long userId) {
        super(CODE, HTTP_STATUS, UserExceptionMessage.NOT_FOUND.getMessage(), ErrorDetail.of("userId", userId, null));
    }
}
