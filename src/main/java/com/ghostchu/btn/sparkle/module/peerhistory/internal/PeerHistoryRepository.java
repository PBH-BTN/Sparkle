package com.ghostchu.btn.sparkle.module.peerhistory.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface PeerHistoryRepository extends SparkleCommonRepository<PeerHistory, Long> {
    Page<PeerHistory> findByOrderByInsertTimeDesc(Pageable pageable);

    long countByInsertTimeBetween(OffsetDateTime insertTimeStart, OffsetDateTime insertTimeEnd);
}
