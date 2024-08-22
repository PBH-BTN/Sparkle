package com.ghostchu.btn.sparkle.module.snapshot.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import com.ghostchu.btn.sparkle.module.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public interface SnapshotRepository extends SparkleCommonRepository<Snapshot, Long> {
    Page<Snapshot> findByOrderByInsertTimeDesc(Pageable pageable);
    long countByInsertTimeBetween(Timestamp insertTimeStart, Timestamp insertTimeEnd);
    //userApplication, peerIp, peerPort, torrent
}
