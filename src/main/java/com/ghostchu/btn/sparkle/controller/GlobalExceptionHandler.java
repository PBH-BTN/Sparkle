package com.ghostchu.btn.sparkle.controller;

import cn.dev33.satoken.exception.NotLoginException;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.exception.UserBannedException;
import com.ghostchu.btn.sparkle.wrapper.StdResp;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<StdResp<Void>> businessExceptionHandler(BusinessException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new StdResp<>(false, e.getMessage(), null));
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<StdResp<Void>> userBannedException(UserBannedException e) {
        return ResponseEntity.status(403).body(new StdResp<>(false, "此用户已被管理员停用，请与系统管理员联系以获取更多信息。", null));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<StdResp<Void>> businessExceptionHandler(NotLoginException e) {
        return ResponseEntity.status(403).body(new StdResp<>(false, "未登录或会话已过期，请转到首页登录", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StdResp<Void>> noResourceFoundException(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new StdResp<>(false, "404 Not Found - 资源未找到", null));
    }

//    @ExceptionHandler(ClientAbortException.class)
//    public void clientAbort(Exception e) {
//        log.warn("Client abort a connection: {}", e.getMessage());
//    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StdResp<Void>> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "无效参数: " + e.getMessage(), null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<StdResp<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "不允许的请求方式: " + e.getMessage(), null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StdResp<Void>> httpMessageNotReadable(HttpMessageNotReadableException e) {
        int loop = 0;
        Throwable exception = e;
        while (exception.getCause() != null) {
            loop++;
            if (loop > 30) break;
            exception = exception.getCause();
            if (exception instanceof ClientAbortException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new StdResp<>(false, "客户端已放弃请求: " + e.getMessage(), null));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new StdResp<>(false, "不可读的 HTTP 消息: " + e.getMessage(), null));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncReqNotUsable(AsyncRequestNotUsableException e) {
        //log.warn("Unable to complete async request because: [{}], async request not usable.", e.getMessage());
        // not my issue
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<StdResp<Void>> jvmExceptionHandler(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new StdResp<>(false, "服务器内部错误，请联系服务器管理员。", null));
    }
}
