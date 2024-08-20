package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface TrackedTaskRepository extends SparkleCommonRepository<TrackedTask, Long> {
    Optional<TrackedTask> findByTorrentInfoHash(String torrentInfoHash);
}