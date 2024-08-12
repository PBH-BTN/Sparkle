package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Unable to find requested user");
    }
}
