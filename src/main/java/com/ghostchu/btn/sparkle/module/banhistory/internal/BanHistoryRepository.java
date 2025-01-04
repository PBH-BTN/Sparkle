package com.ghostchu.btn.sparkle.module.banhistory.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BanHistoryRepository extends SparkleCommonRepository<BanHistory, Long> {
    @Query("""
                SELECT DISTINCT ban.peerIp
                       FROM BanHistory ban
                       WHERE
                          ban.insertTime >= ?1 AND ban.insertTime <= ?2 AND ban.module = 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker'
                          AND ban.userApplication.bannedAt IS NULL AND ban.userApplication.user.bannedAt IS NULL
                       GROUP BY ban.peerIp
                       HAVING COUNT(DISTINCT ban.userApplication) >= ?3
            """)
    @Transactional
    List<InetAddress> generateUntrustedIPAddresses(OffsetDateTime from, OffsetDateTime to, int threshold);


    //    @Query("""
//                SELECT DISTINCT ban.peerIp FROM BanHistory ban
//                WHERE
//                    family(ban.peerIp) = ?3
//                    AND ban.insertTime >= ?1 AND ban.insertTime <= ?2
//                    AND ban.module LIKE '%ProgressCheatBlocker%'
//                GROUP BY ban.peerIp
//            """)
//    @Transactional
//    List<InetAddress> findByInsertTimeBetweenOrderByInsertTimeDescIPVx(OffsetDateTime from, OffsetDateTime to, int family);
//
    Page<BanHistory> findByOrderByInsertTimeDesc(Pageable pageable);

    long countByInsertTimeBetween(OffsetDateTime insertTimeStart, OffsetDateTime insertTimeEnd);


    List<BanHistory> findDistinctByInsertTimeBetweenAndPeerClientNameLike(OffsetDateTime from, OffsetDateTime to, String peerClientName);

    List<BanHistory> findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(OffsetDateTime from, OffsetDateTime to, String module, String peerClientName);
}
