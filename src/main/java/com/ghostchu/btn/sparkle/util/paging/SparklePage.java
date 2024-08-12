package com.ghostchu.btn.sparkle.util.paging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SparklePage<T> {
    private int page;
    private int size;
    private long total;
    private T results;
}
