package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class UserApplicationNotFoundException extends BusinessException {
    public UserApplicationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "请求的用户应用程序不存在");
    }
}
