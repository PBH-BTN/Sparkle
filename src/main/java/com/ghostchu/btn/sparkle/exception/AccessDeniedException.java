package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedException extends BusinessException{
    public AccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "拒绝访问: " + message);
    }
    public AccessDeniedException() {
        super(HttpStatus.FORBIDDEN, "拒绝访问");
    }
}
