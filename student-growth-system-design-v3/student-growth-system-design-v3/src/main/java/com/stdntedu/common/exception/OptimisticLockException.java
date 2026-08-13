package com.stdntedu.common.exception;

import org.springframework.http.HttpStatus;

public class OptimisticLockException extends BusinessException {
    public OptimisticLockException(String message) {
        super("DATA_VERSION_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
