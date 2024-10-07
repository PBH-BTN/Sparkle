package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface TrackedPeerRepository extends SparkleCommonRepository<TrackedPeer, Long> {


    @Query("""
            select t from TrackedPeer t
            where t.torrentInfoHash = ?1 and t.peerId <> ?2 and t.peerIp <> ?3
            order by RANDOM() limit ?4
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, String peerId, InetAddress peerIp, int limit);

    @Query("""
            select t from TrackedPeer t
            where t.torrentInfoHash = ?1 
            order by RANDOM() limit ?2
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, int limit);

    Optional<TrackedPeer> findByPeerIpAndPeerIdAndTorrentInfoHash(InetAddress peerIp, String peerId, String torrentInfoHash);

    long deleteByLastTimeSeenLessThanEqual(OffsetDateTime deleteAllEntireBeforeThisTime);

    long countByTorrentInfoHashAndLeft(String torrentInfoHash, Long left);

    long countByTorrentInfoHashAndLeftNot(String torrentInfoHash, Long left);

    @Query("select count(distinct t.torrentInfoHash) from TrackedPeer t")
    long countTrackingTorrents();

    long countByLeft(Long left);

    long countByLeftNot(Long left);

    @Query("select count(*) from TrackedPeer t where t.uploaded = 0")
    long countUsersWhoDidntUploadAnyData();
    @Query("select count(*) from TrackedPeer t where t.uploaded != 0")
    long countUsersWhoUploadedAnyData();

}