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
            INSERT INTO tracker_peers (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, \
                                       torrent_info_hash, uploaded_offset, downloaded_offset, \
                                       "left", last_event, user_agent, last_time_seen, peer_geoip, support_crypto, \
                                       "key", crypto_port, azudp, hide, azhttp, corrupt, redundant, tracker_id, azq, \
                                       azver, azup, azas, aznp, num_want) \
            VALUES (:reqIp, :peerId, :peerIdHumanReadable, :peerIp, :peerPort, :torrentInfoHash, \
                    :uploadedOffset, :downloadedOffset, :left, :lastEvent, :userAgent, \
                    :lastTimeSeen, CAST(:peerGeoIP AS jsonb), :supportCrypto, :key, :cryptoPort, :azudp, :hide, :azhttp, \
                    :corrupt, :redundant, :trackerId, :azq, :azver, :azup, :azas, :aznp, :numWant) \
            ON CONFLICT (peer_id, torrent_info_hash) DO UPDATE SET \
            uploaded_offset = EXCLUDED.uploaded_offset, \
            downloaded_offset = EXCLUDED.downloaded_offset, \
            "left" = EXCLUDED."left", \
            last_event = EXCLUDED.last_event, \
            user_agent = EXCLUDED.user_agent, \
            last_time_seen = EXCLUDED.last_time_seen, \
            peer_geoip = CAST(EXCLUDED.peer_geoip AS jsonb), \
            support_crypto = EXCLUDED.support_crypto, \
            "key" = EXCLUDED.key, \
            crypto_port = EXCLUDED.crypto_port, \
            azudp = EXCLUDED.azudp, \
            hide = EXCLUDED.hide, \
            azhttp = EXCLUDED.azhttp, \
            corrupt = EXCLUDED.corrupt, \
            redundant = EXCLUDED.redundant, \
            tracker_id = EXCLUDED.tracker_id, \
            azq = EXCLUDED.azq, \
            azver = EXCLUDED.azver, \
            azup = EXCLUDED.azup, \
            azas = EXCLUDED.azas, \
            aznp = EXCLUDED.aznp, \
            num_want = EXCLUDED.num_want""",
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
                           @Param("supportCrypto") Boolean supportCrypto, // Fixed case
                           @Param("cryptoPort") Long cryptoPort,
                           @Param("key") String key,
                           @Param("azudp") Long azudp,
                           @Param("hide") Boolean hide,
                           @Param("azhttp") Long azhttp,
                           @Param("corrupt") Long corrupt,
                           @Param("redundant") Long redundant,
                           @Param("trackerId") String trackerId, // Fixed case
                           @Param("azq") Boolean azq,
                           @Param("azver") String azver,
                           @Param("azup") Long azup,
                           @Param("azas") String azas,
                           @Param("aznp") String aznp,
                           @Param("numWant") long numWant);

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