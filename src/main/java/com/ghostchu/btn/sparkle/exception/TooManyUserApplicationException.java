package com.ghostchu.btn.sparkle.exception;

import org.springframework.http.HttpStatus;

public class TooManyUserApplicationException extends BusinessException{

    public TooManyUserApplicationException() {
        super(HttpStatus.INSUFFICIENT_STORAGE, "您所拥有的用户应用程序数量已达上限");
    }
}
