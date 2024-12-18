package com.ghostchu.btn.sparkle.module.snapshot.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface SnapshotRepository extends SparkleCommonRepository<Snapshot, Long> {
    Page<Snapshot> findByOrderByInsertTimeDesc(Pageable pageable);

    long countByInsertTimeBetween(OffsetDateTime insertTimeStart, OffsetDateTime insertTimeEnd);

    long deleteByInsertTimeBefore(OffsetDateTime offsetDateTime);

    long deleteAllByInsertTimeBefore(OffsetDateTime offsetDateTime);
}
