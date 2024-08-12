package com.ghostchu.btn.sparkle.torrent.internal;

import com.ghostchu.btn.sparkle.repository.SparkleCommonRepository;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface TorrentRepository extends SparkleCommonRepository<Torrent, Long> {
    Optional<Torrent> findByIdentifierAndSize(@NonNull String identifier, @NonNull Long size);
}
