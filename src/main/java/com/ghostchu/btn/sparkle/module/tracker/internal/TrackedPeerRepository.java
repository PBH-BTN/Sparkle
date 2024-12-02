package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TrackedPeerRepository extends SparkleCommonRepository<TrackedPeer, Long> {

    @Query("""
            select t from TrackedPeer t
            where t.pk.torrentInfoHash = ?1 and t.pk.peerId <> ?2 and t.peerIp <> ?3
            order by RANDOM() limit ?4
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, String peerId, InetAddress peerIp, int limit);

    @Query(value = """
            with filtered_peers as (
                select *
                from tracked_peer t
                where t.torrent_info_hash = :torrentInfoHash and t.peer_id <> :peerId
            )
            select *
            from filtered_peers
            order by random()
            limit :limit
            """, nativeQuery = true)
    List<TrackedPeer> fetchPeersFromTorrent(
            @Param("torrentInfoHash") String torrentInfoHash,
            @Param("peerId") String peerId,
            @Param("limit") int limit);


    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO tracker_peers (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, \
                                       torrent_info_hash, uploaded_offset, downloaded_offset, \
                                       "left", last_event, user_agent, last_time_seen, peer_geoip) \
            VALUES (:reqIp, :peerId, :peerIdHumanReadable, :peerIp, :peerPort, :torrentInfoHash, \
                    :uploadedOffset, :downloadedOffset, :left, :lastEvent, :userAgent, \
                    :lastTimeSeen, CAST(:peerGeoIP AS jsonb)) \
            ON CONFLICT (peer_id, torrent_info_hash) DO UPDATE SET \
            uploaded_offset = EXCLUDED.uploaded_offset, \
            downloaded_offset = EXCLUDED.downloaded_offset, \
            "left" = EXCLUDED."left", \
            last_event = EXCLUDED.last_event, \
            user_agent = EXCLUDED.user_agent, \
            last_time_seen = EXCLUDED.last_time_seen, \
            peer_geoip = CAST(EXCLUDED.peer_geoip AS jsonb)""",
            nativeQuery = true)
    void upsertTrackedPeer(@Param("reqIp") InetAddress reqIp,
                           @Param("peerId") String peerId,
                           @Param("peerIdHumanReadable") String peerIdHumanReadable,
                           @Param("peerIp") InetAddress peerIp,
                           @Param("peerPort") Integer peerPort,
                           @Param("torrentInfoHash") String torrentInfoHash,
                           @Param("uploadedOffset") Long uploadedOffset,
                           @Param("downloadedOffset") Long downloadedOffset,
                           @Param("left") Long left,
                           @Param("lastEvent") int lastEvent,
                           @Param("userAgent") String userAgent,
                           @Param("lastTimeSeen") OffsetDateTime lastTimeSeen,
                           @Param("peerGeoIP") String peerGeoIP);

    void deleteByPk_PeerIdAndPk_TorrentInfoHash(String peerId, String infoHash);

    long deleteByLastTimeSeenLessThanEqual(OffsetDateTime deleteAllEntireBeforeThisTime);

    long countByPk_TorrentInfoHashAndLeft(String torrentInfoHash, Long left);

    long countByPk_TorrentInfoHashAndLeftNot(String torrentInfoHash, Long left);

    @Query("select count(distinct t.pk.torrentInfoHash) from TrackedPeer t")
    long countTrackingTorrents();

    long countByLeft(Long left);

    long countByLeftNot(Long left);

    long countDistinctPeerIdBy();

    long countDistinctPeerIpBy();

    long countDistinctTorrentInfoHashBy();

}