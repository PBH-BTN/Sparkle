package com.ghostchu.btn.sparkle.wrapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class StdResp<T> {
    private boolean success;
    private String message;
    private T data;
}
