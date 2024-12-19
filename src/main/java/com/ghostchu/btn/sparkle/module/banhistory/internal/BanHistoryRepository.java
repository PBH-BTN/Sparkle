package com.ghostchu.btn.sparkle.module.banhistory.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface BanHistoryRepository extends SparkleCommonRepository<BanHistory, Long> {
    @Query("""
                SELECT ban.peerIp
                       FROM BanHistory ban
                       WHERE
                          ban.insertTime >= ?1 AND ban.insertTime <= ?2 AND ban.module LIKE '%ProgressCheatBlocker%'
                       GROUP BY ban.peerIp, time_bucket(?4, ban.insertTime)
                       HAVING COUNT(DISTINCT ban.userApplication.appId) >= ?3
            """)
    @Transactional
    List<InetAddress> generateUntrustedIPAddresses(OffsetDateTime from, OffsetDateTime to, int threshold, Duration timeBucket);

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

    @Query("""
                SELECT DISTINCT ban FROM BanHistory ban
                WHERE
                    ban.insertTime >= ?1 AND ban.insertTime <= ?2 AND
                    (ban.peerId LIKE ?3 OR ban.peerClientName LIKE ?4)
            """)
    @Transactional
    List<BanHistory> findDistinctByPeerIdLikeOrPeerClientNameLike(OffsetDateTime from, OffsetDateTime to, String peerId, String peerClientName);

    @Query(nativeQuery = true, value = "SELECT * from banhistory ban WHERE ban.insert_time >= ?2 AND ban.insert_time <= ?3 AND host(ban.peer_ip) LIKE ?1")
    @Transactional
    List<BanHistory> findByPeerIp(String peerIp, OffsetDateTime insertTimeStart, OffsetDateTime insertTimeEnd);


    List<BanHistory> findDistinctByInsertTimeBetweenAndPeerClientNameLike(OffsetDateTime from, OffsetDateTime to, String peerClientName);

    List<BanHistory> findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(OffsetDateTime from, OffsetDateTime to, String module, String peerClientName);
}
