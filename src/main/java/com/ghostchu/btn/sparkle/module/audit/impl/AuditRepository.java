package com.ghostchu.btn.sparkle.module.audit.impl;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRepository extends SparkleCommonRepository<Audit, Long> {

}
