package com.mist.commerce.global.exception;

import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandNameDuplicatedException;
import com.mist.commerce.domain.brand.exception.BrandRegistrationForbiddenException;
import com.mist.commerce.global.response.ApiResponse;
import com.mist.commerce.global.response.ErrorDetail;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(UserEmailDuplicatedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserEmailDuplicated(UserEmailDuplicatedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("USER_EMAIL_DUPLICATED", ex.getMessage(), clock.instant()));
    }

    @ExceptionHandler(BrandRegistrationForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleBrandRegistrationForbidden(
            BrandRegistrationForbiddenException ex
    ) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage(), clock.instant()));
    }

    @ExceptionHandler(BrandNameDuplicatedException.class)
    public ResponseEntity<ApiResponse<Void>> handleBrandNameDuplicated(BrandNameDuplicatedException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage(), clock.instant()));
    }

    @ExceptionHandler(OAuthAccountAlreadyLinkedToBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleOAuthAccountAlreadyLinkedToBusiness(
            OAuthAccountAlreadyLinkedToBusinessException ex
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(
                        "OAUTH_ACCOUNT_ALREADY_LINKED_TO_BUSINESS",
                        ex.getMessage(),
                        clock.instant()
                ));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("INVALID_TOKEN", ex.getMessage(), clock.instant()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorDetail(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        "VALIDATION_ERROR",
                        "입력값이 올바르지 않습니다.",
                        errors,
                        clock.instant()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        "INTERNAL_SERVER_ERROR",
                        "예상하지 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                        clock.instant()
                ));
    }
}
