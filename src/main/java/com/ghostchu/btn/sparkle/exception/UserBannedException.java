package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class UserBannedException extends BusinessException {

    public UserBannedException() {
        super(HttpStatus.FORBIDDEN, "User is banned");
    }
}
