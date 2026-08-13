package com.stdntedu.common.web;

import java.util.List;

import com.stdntedu.common.api.ApiResponse;
import com.stdntedu.common.api.ApiResponseFactory;
import com.stdntedu.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ApiResponseFactory responses;

    public GlobalExceptionHandler(ApiResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> validation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<ApiResponseFactory.FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError).toList();
        return body("VALIDATION_ERROR", "request validation failed", HttpStatus.UNPROCESSABLE_ENTITY, request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> validation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<ApiResponseFactory.FieldErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiResponseFactory.FieldErrorItem(v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .toList();
        return body("VALIDATION_ERROR", "request validation failed", HttpStatus.UNPROCESSABLE_ENTITY, request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> badRequest(Exception ex, HttpServletRequest request) {
        return body("BAD_REQUEST", "malformed request", HttpStatus.BAD_REQUEST, request, List.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> business(BusinessException ex, HttpServletRequest request) {
        return body(ex.getCode(), ex.getMessage(), ex.getStatus(), request, List.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> duplicate(HttpServletRequest request) {
        return body("DUPLICATE_DATA", "duplicate data", HttpStatus.CONFLICT, request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> tooLarge(HttpServletRequest request) {
        return body("PAYLOAD_TOO_LARGE", "payload too large", HttpStatus.PAYLOAD_TOO_LARGE, request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> missing(HttpServletRequest request) {
        return body("NOT_FOUND", "resource not found", HttpStatus.NOT_FOUND, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> unexpected(HttpServletRequest request) {
        return body("INTERNAL_SERVER_ERROR", "internal server error", HttpStatus.INTERNAL_SERVER_ERROR, request, List.of());
    }

    private ApiResponseFactory.FieldErrorItem fieldError(FieldError error) {
        return new ApiResponseFactory.FieldErrorItem(error.getField(), error.getDefaultMessage(), error.getRejectedValue());
    }

    private ResponseEntity<ApiResponse<ApiResponseFactory.ErrorBody>> body(String code, String message, HttpStatus status,
            HttpServletRequest request, List<ApiResponseFactory.FieldErrorItem> errors) {
        return ResponseEntity.status(status).body(responses.error(code, message, requestId(request), errors));
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader(RequestIdFilter.HEADER);
    }
}
