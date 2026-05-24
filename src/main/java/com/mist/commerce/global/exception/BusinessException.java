package com.mist.commerce.global.exception;

import com.mist.commerce.global.response.ErrorDetail;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final ErrorDetail errorDetail;

    protected BusinessException(String code, HttpStatus httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.errorDetail = null;
    }

    protected BusinessException(String code, HttpStatus httpStatus, String message, ErrorDetail errorDetail) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.errorDetail = errorDetail;
    }

    protected BusinessException(String code, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.errorDetail = null;
    }

    protected BusinessException(String code, HttpStatus httpStatus, String message, ErrorDetail errorDetail, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.errorDetail = errorDetail;
    }
}
