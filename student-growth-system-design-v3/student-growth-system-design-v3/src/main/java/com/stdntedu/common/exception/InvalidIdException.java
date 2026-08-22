package com.stdntedu.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidIdException extends BusinessException {
    public InvalidIdException() {
        super("BAD_REQUEST", "invalid API ID", HttpStatus.BAD_REQUEST);
    }
}
