package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class RequestPageSizeTooLargeException extends BusinessException{
    public RequestPageSizeTooLargeException() {
        super(HttpStatus.BAD_REQUEST, "请求的分页数量参数超过最大允许值");
    }
}
