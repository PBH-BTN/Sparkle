package com.ghostchu.btn.sparkle.util.paging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SparklePage<Entity,Dto> implements Serializable {
    private int page;
    private int size;
    private long total;
    private List<Dto> results;

    public SparklePage(Page<Entity> page, Function<Stream<Entity>, Stream<Dto>> mapper){
        this.page = page.getPageable().getPageNumber();
        this.size = page.getPageable().getPageSize();
        this.total = page.getTotalElements();
        this.results = mapper.apply(page.getContent().stream()).toList();
    }
}
