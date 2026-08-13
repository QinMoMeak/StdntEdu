package com.stdntedu.common.exception;

import org.springframework.http.HttpStatus;

public class DataConflictException extends BusinessException {
    public DataConflictException(String message) {
        super("CONFLICT", message, HttpStatus.CONFLICT);
    }
}
