package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
@Repository
public interface TrackedPeerRepository extends SparkleCommonRepository<TrackedPeer, Long> {

    @Query("""
            select t from TrackedPeer t
            where t.id.torrentInfoHash = ?1 and t.id.peerId <> ?2 and t.peerIp <> ?3
            order by RANDOM() limit ?4
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, String peerId, InetAddress peerIp, int limit);

    @Query("""
             select t from TrackedPeer t
             where t.id.torrentInfoHash = ?1 and t.id.peerId <> ?2 and t.lastEvent <> ?3
             order by RANDOM() limit ?4
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, String peerId, PeerEvent excludeEvent, int limit);


    long deleteByLastTimeSeenLessThanEqual(OffsetDateTime deleteAllEntireBeforeThisTime);

    long deleteByLastEvent(PeerEvent peerEvent);

    long countById_TorrentInfoHashAndLeftNot(String torrentInfoHash, Long left);

    @Query("select count(distinct t.id.torrentInfoHash) from TrackedPeer t")
    long countTrackingTorrents();

    long countByLeft(Long left);

    long countByLeftNot(Long left);

    long countDistinctPeerIdBy();

    long countDistinctPeerIpBy();

    long countDistinctTorrentInfoHashBy();

    @Query("select count(*) from TrackedPeer t where t.uploaded = 0")
    long countUsersWhoDidntUploadAnyData();
    @Query("select count(*) from TrackedPeer t where t.uploaded != 0")
    long countUsersWhoUploadedAnyData();

}