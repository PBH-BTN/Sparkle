package com.ghostchu.btn.sparkle.snapshot.internal;

import com.ghostchu.btn.sparkle.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnapshotRepository extends SparkleCommonRepository<Snapshot, Long> {

}
