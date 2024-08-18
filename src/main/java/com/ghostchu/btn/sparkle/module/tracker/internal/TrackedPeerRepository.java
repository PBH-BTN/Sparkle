package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
@Repository
public interface TrackedPeerRepository extends SparkleCommonRepository<TrackedPeer, Long> {
    @Query("""
            select t from TrackedPeer t
            where t.torrentInfoHash = ?1 and t.peerId <> ?2 and t.peerIp <> ?3
            order by RANDOM() limit ?4
            """)
    List<TrackedPeer> fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int limit);

    @Query("""
            select t from TrackedPeer t
            where t.torrentInfoHash = ?1 
            order by RANDOM() limit ?2
            """)
    List<TrackedPeer> fetchPeersFromTorrent(byte[] torrentInfoHash, int limit);

    Optional<TrackedPeer> findByPeerIpAndPeerIdAndTorrentInfoHash(InetAddress peerIp, byte[] peerId, byte[] torrentInfoHash);

    long deleteByLastTimeSeenLessThanEqual(Timestamp deleteAllEntireBeforeThisTime);

    long countByTorrentInfoHashAndLeft(byte[] torrentInfoHash, Long left);

    long countByTorrentInfoHashAndLeftNot(byte[] torrentInfoHash, Long left);


}