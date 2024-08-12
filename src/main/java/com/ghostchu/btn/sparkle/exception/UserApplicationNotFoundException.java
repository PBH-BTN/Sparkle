package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class UserApplicationNotFoundException extends BusinessException {
    public UserApplicationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Requested user application not found");
    }
}
