package com.ghostchu.btn.sparkle.module.banhistory.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.List;

@Repository
public interface BanHistoryRepository extends SparkleCommonRepository<BanHistory, Long> {
    @Query("""
                SELECT DISTINCT ban.peerIp FROM BanHistory ban
                WHERE 
                    ban.module LIKE '%ProgressCheatBlocker%'
                    AND ban.insertTime >= ?1 AND ban.insertTime <= ?2
                GROUP BY ban.peerIp
                HAVING COUNT (DISTINCT ban.userApplication.appId) >= ?3
            """)
    List<InetAddress> generateUntrustedIPAddresses(Timestamp from, Timestamp to, int threshold);

    Page<BanHistory> findByInsertTimeBetweenOrderByInsertTimeDesc(Timestamp from, Timestamp to, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerIdLikeIgnoreCaseOrderByInsertTimeDesc(Timestamp from, Timestamp to, String peerId, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerClientNameLikeIgnoreCaseOrderByInsertTimeDesc(Timestamp from, Timestamp to, String peerClientName, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerIpEqualsOrderByInsertTimeDesc(Timestamp from, Timestamp to, InetAddress peerIp, Pageable pageable);

    Page<BanHistory> findByOrderByInsertTimeDesc(Pageable pageable);

    long countByInsertTimeBetween(Timestamp insertTimeStart, Timestamp insertTimeEnd);


}
