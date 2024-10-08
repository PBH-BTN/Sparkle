package com.ghostchu.btn.sparkle.module.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;

public interface SparkleCommonRepository<T, ID> extends CrudRepository<T, ID>, JpaSpecificationExecutor<T> {
}
