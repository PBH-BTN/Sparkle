package com.ghostchu.btn.sparkle.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.wrapper.StdResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StdResp<Void>> businessExceptionHandler(BusinessException e){
        return ResponseEntity.status(e.getStatusCode()).body(new StdResp<>(false, e.getMessage(), null));
    }
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<StdResp<Void>> businessExceptionHandler(NotLoginException e){
        return ResponseEntity.status(403).body(new StdResp<>(false,"未登录或会话已过期，请转到首页登录", null));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StdResp<Void>> jvmExceptionHandler(Exception e){
        log.error("Unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StdResp<>(false, "服务器内部错误，请联系服务器管理员。", null));
    }
}
