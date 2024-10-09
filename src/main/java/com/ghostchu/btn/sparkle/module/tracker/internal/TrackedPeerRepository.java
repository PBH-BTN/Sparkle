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

    @Query("""
            select t from TrackedPeer t
            where t.pk.torrentInfoHash = ?1 and t.pk.peerId <> ?2
            order by RANDOM() limit ?3
            """)
    List<TrackedPeer> fetchPeersFromTorrent(String torrentInfoHash, String peerId, int limit);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO tracker_peers (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, torrent_info_hash, uploaded_offset, downloaded_offset, "left", last_event, user_agent, last_time_seen, peer_geoip, request_geoip, version) \
            VALUES (:reqIp, :peerId, :peerIdHumanReadable, :peerIp, :peerPort, :torrentInfoHash, :uploadedOffset, :downloadedOffset, :left, :lastEvent, :userAgent, :lastTimeSeen, CAST(:peerGeoIP AS jsonb), CAST(:requestGeoIP AS jsonb), :version) \
            ON CONFLICT (peer_ip, peer_id, torrent_info_hash) DO UPDATE SET \
            uploaded = CASE \
              WHEN tracker_peers.uploaded_offset > EXCLUDED.uploaded_offset THEN tracker_peers.uploaded + EXCLUDED.uploaded_offset \
              ELSE tracker_peers.uploaded + (EXCLUDED.uploaded_offset - tracker_peers.uploaded_offset) \
            END, \
            uploaded_offset = EXCLUDED.uploaded_offset, \
            downloaded = CASE \
              WHEN tracker_peers.downloaded_offset > EXCLUDED.downloaded_offset THEN tracker_peers.downloaded + EXCLUDED.downloaded_offset \
              ELSE tracker_peers.downloaded + (EXCLUDED.downloaded_offset - tracker_peers.downloaded_offset) \
            END, \
            downloaded_offset = EXCLUDED.downloaded_offset, \
            "left" = EXCLUDED."left", \
            last_event = EXCLUDED.last_event, \
            user_agent = EXCLUDED.user_agent, \
            last_time_seen = EXCLUDED.last_time_seen, \
            peer_geoip = CAST(EXCLUDED.peer_geoip AS jsonb), \
            request_geoip = CAST(EXCLUDED.request_geoip AS jsonb), \
            version = EXCLUDED.version""",
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
                           @Param("peerGeoIP") String peerGeoIP,
                           @Param("requestGeoIP") String requestGeoIP,
                           @Param("version") Integer version);

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

    @Query("select count(*) from TrackedPeer t where t.uploaded = 0")
    long countUsersWhoDidntUploadAnyData();
    @Query("select count(*) from TrackedPeer t where t.uploaded != 0")
    long countUsersWhoUploadedAnyData();

}