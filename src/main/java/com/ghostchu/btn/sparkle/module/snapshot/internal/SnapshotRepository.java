package com.ghostchu.btn.sparkle.module.snapshot.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnapshotRepository extends SparkleCommonRepository<Snapshot, Long> {

}
