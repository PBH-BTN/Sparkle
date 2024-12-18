package com.ghostchu.btn.sparkle.module.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

import java.util.function.Consumer;

public interface SparkleCommonRepository<T, ID> extends CrudRepository<T, ID>, JpaSpecificationExecutor<T> {
    default void findAllByPaging(Specification<T> specification, Consumer<Page<T>> consumer) {
        PageRequest request = PageRequest.of(0, 500);
        Page<T> page;
        while (true) {
            page = findAll(specification, request);
            consumer.accept(page);
            if (page.hasNext()) {
                request = request.next();
            } else {
                break;
            }
        }
    }
}
