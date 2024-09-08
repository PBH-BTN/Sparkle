package com.ghostchu.btn.sparkle.module.banhistory.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import jakarta.transaction.Transactional;
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
    @Transactional
    List<InetAddress> generateUntrustedIPAddresses(Timestamp from, Timestamp to, int threshold);

    Page<BanHistory> findByInsertTimeBetweenOrderByInsertTimeDesc(Timestamp from, Timestamp to, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerIdLikeIgnoreCaseOrderByInsertTimeDesc(Timestamp from, Timestamp to, String peerId, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerClientNameLikeIgnoreCaseOrderByInsertTimeDesc(Timestamp from, Timestamp to, String peerClientName, Pageable pageable);

    Page<BanHistory> findByInsertTimeBetweenAndPeerIpEqualsOrderByInsertTimeDesc(Timestamp from, Timestamp to, InetAddress peerIp, Pageable pageable);

    Page<BanHistory> findByOrderByInsertTimeDesc(Pageable pageable);

    long countByInsertTimeBetween(Timestamp insertTimeStart, Timestamp insertTimeEnd);

    @Query("""
                SELECT DISTINCT ban FROM BanHistory ban
                WHERE
                    (ban.peerId LIKE ?1 OR ban.peerClientName LIKE ?2)
                    AND ban.insertTime >= ?3 AND ban.insertTime <= ?4
            """)
    @Transactional
    List<BanHistory> findDistinctByPeerIdLikeOrPeerClientNameLike(String peerId, String peerClientName, Timestamp from, Timestamp to);

    List<BanHistory> findDistinctByPeerIdLikeAndInsertTimeBetween(String peerId, Timestamp from, Timestamp to);

    @Query(nativeQuery = true, value = "SELECT * from banhistory ban WHERE ban.insert_time >= ?2 AND ban.insert_time <= ?3 AND host(ban.peer_ip) LIKE ?1")
    @Transactional
    List<BanHistory> findByPeerIp(String peerIp,Timestamp insertTimeStart, Timestamp insertTimeEnd);

    List<BanHistory> findDistinctByPeerClientNameLikeAndInsertTimeBetween(String peerClientName, Timestamp from, Timestamp to);

    List<BanHistory> findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween(String peerClientName, String module, Timestamp from, Timestamp to);
}
