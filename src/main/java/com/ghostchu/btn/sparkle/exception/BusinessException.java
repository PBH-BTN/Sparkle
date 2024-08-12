package com.ghostchu.btn.sparkle.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatusCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BusinessException extends Exception {
    private final HttpStatusCode statusCode;

    public BusinessException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

}
