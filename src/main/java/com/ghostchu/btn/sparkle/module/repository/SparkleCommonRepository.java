package com.ghostchu.btn.sparkle.module.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SparkleCommonRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
}
