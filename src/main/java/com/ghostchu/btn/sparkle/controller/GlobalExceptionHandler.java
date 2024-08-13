package com.ghostchu.btn.sparkle.controller;

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
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StdResp<Void>> jvmExceptionHandler(Exception e){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StdResp<>(false, "服务器内部错误，请联系服务器管理员："+e.getClass().getName()+": "+e.getMessage(), null));
    }
}
