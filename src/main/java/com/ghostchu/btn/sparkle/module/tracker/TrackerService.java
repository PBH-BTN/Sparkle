package com.ghostchu.btn.sparkle.module.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class TrackerService {

    private final TrackedPeerRepository trackedPeerRepository;
    private final long inactiveInterval;
    private final int maxPeersReturn;
    private final GeoIPManager geoIPManager;
    private final Counter announceCounter;
    private final Counter peersFetchCounter;
    private final Counter scrapeCounter;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper jacksonObjectMapper;
    private final Semaphore parallelSave;
    private final Semaphore parallelAnnounce;
    private final Deque<PeerAnnounce> announceDeque = new ConcurrentLinkedDeque<>();
    private final ReentrantLock announceFlushLock = new ReentrantLock();
    private final NamedParameterJdbcTemplate jdbcTemplate;


    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn, GeoIPManager geoIPManager,
                          MeterRegistry meterRegistry, ObjectMapper jacksonObjectMapper,
                          @Value("${service.tracker.max-parallel-announce}") int maxParallelAnnounce,
                          @Value("${service.tracker.max-parallel-announce-save}") int maxParallelAnnounceSave,
                          NamedParameterJdbcTemplate jdbcTemplate) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
        this.geoIPManager = geoIPManager;
        this.meterRegistry = meterRegistry;
        this.announceCounter = meterRegistry.counter("sparkle_tracker_announce");
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.parallelSave = new Semaphore(maxParallelAnnounceSave);
        this.parallelAnnounce = new Semaphore(maxParallelAnnounce);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRateString = "${service.tracker.metrics-interval}")
    @Transactional
    public void updateTrackerMetrics() {
        var totalPeers = meterRegistry.gauge("sparkle_tracker_tracking_total_peers", trackedPeerRepository.count());
        var uniquePeers = meterRegistry.gauge("sparkle_tracker_tracking_unique_peers", trackedPeerRepository.countDistinctPeerIdBy());
        var uniqueIps = meterRegistry.gauge("sparkle_tracker_tracking_unique_ips", trackedPeerRepository.countDistinctPeerIpBy());
        var activeTasks = meterRegistry.gauge("sparkle_tracker_tracking_active_tasks", trackedPeerRepository.countDistinctTorrentInfoHashBy());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}", totalPeers, uniquePeers, uniqueIps, activeTasks);
    }

    @Scheduled(fixedRateString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = trackedPeerRepository.deleteByLastTimeSeenLessThanEqual(TimeUtil.toUTC(System.currentTimeMillis() - inactiveInterval));
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    public void scheduleAnnounce(PeerAnnounce announce) {
        if (announceDeque.size() > 20000) {
            throw new RuntimeException("Server is busy! More than 20,000 announces are waiting to be processed.");
        }
        announceDeque.offer(announce);
    }

    public void executeAnnounce() {
        List<PeerAnnounce> batch = new ArrayList<>(3000);

        // 从队列中取出任务
        while (!announceDeque.isEmpty() && batch.size() < 3000) {
            PeerAnnounce announce = announceDeque.poll();
            if (announce != null) {
                batch.add(announce);
            }
        }

        if (batch.isEmpty()) {
            return; // 队列为空，直接返回
        }

        // 按 peerId 和 infoHash 分组，只保留最后一个事件
        Map<String, PeerAnnounce> latestAnnounceMap = new HashMap<>();
        for (PeerAnnounce announce : batch) {
            String key = ByteUtil.bytesToHex(announce.peerId()) + "-" + ByteUtil.bytesToHex(announce.infoHash());
            latestAnnounceMap.put(key, announce);
        }

        List<PeerAnnounce> latestAnnounces = new ArrayList<>(latestAnnounceMap.values());

        // 分为 STOPPED 和其他事件
        List<PeerAnnounce> stoppedAnnounces = new ArrayList<>();
        List<PeerAnnounce> activeAnnounces = new ArrayList<>();

        for (PeerAnnounce announce : latestAnnounces) {
            if (announce.peerEvent() == PeerEvent.STOPPED) {
                stoppedAnnounces.add(announce);
            } else {
                activeAnnounces.add(announce);
            }
        }

        // 批量删除 STOPPED
        if (!stoppedAnnounces.isEmpty()) {
            List<Map<String, Object>> deleteParams = new ArrayList<>();
            for (PeerAnnounce announce : stoppedAnnounces) {
                Map<String, Object> params = new HashMap<>();
                params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
                params.put("info_hash", ByteUtil.bytesToHex(announce.infoHash()));
                deleteParams.add(params);
            }

            String deleteSql = "DELETE FROM tracker_peers WHERE peer_id = :peer_id AND info_hash = :info_hash";
            jdbcTemplate.batchUpdate(deleteSql, deleteParams.toArray(new Map[0]));
        }

        // 批量插入或更新 active announces
        if (!activeAnnounces.isEmpty()) {
            List<Map<String, Object>> upsertParams = new ArrayList<>();
            try {
                for (PeerAnnounce announce : activeAnnounces) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("req_ip", announce.reqIp());
                    params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
                    params.put("peer_id_human_readable", ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)));
                    params.put("peer_ip", announce.peerIp());
                    params.put("peer_port", announce.peerPort());
                    params.put("torrent_info_hash", ByteUtil.bytesToHex(announce.infoHash()));
                    params.put("uploaded_offset", announce.uploaded());
                    params.put("downloaded_offset", announce.downloaded());
                    params.put("left", announce.left());
                    params.put("last_event", announce.peerEvent().ordinal());
                    params.put("user_agent", ByteUtil.filterUTF8(announce.userAgent()));
                    params.put("last_time_seen", OffsetDateTime.now());
                    params.put("peer_geoip", jacksonObjectMapper.writeValueAsString(geoIPManager.geoData(announce.peerIp())));
                    upsertParams.add(params);
                }

            } catch (Exception e) {
                log.warn("Failed to handle active announce", e);
            }

            String upsertSql = """
                        INSERT INTO tracker_peers
                            (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, torrent_info_hash, 
                             uploaded_offset, downloaded_offset, "left", last_event, user_agent, 
                             last_time_seen, peer_geoip)
                        VALUES 
                            (:req_ip, :peer_id, :peer_id_human_readable, :peer_ip, :peer_port, :torrent_info_hash, 
                             :uploaded_offset, :downloaded_offset, :left, :last_event, :user_agent, 
                             :last_time_seen, :peer_geoip)
                        ON CONFLICT (peer_id, torrent_info_hash)
                        DO UPDATE SET 
                            req_ip = EXCLUDED.req_ip,
                            peer_ip = EXCLUDED.peer_ip,
                            peer_port = EXCLUDED.peer_port,
                            uploaded_offset = EXCLUDED.uploaded_offset,
                            downloaded_offset = EXCLUDED.downloaded_offset,
                            "left" = EXCLUDED."left",
                            last_event = EXCLUDED.last_event,
                            user_agent = EXCLUDED.user_agent,
                            last_time_seen = EXCLUDED.last_time_seen,
                            peer_geoip = EXCLUDED.peer_geoip
                    """;
            jdbcTemplate.batchUpdate(upsertSql, upsertParams.toArray(new Map[0]));
        }
    }


    @Modifying
    @Scheduled(fixedRateString = "${service.tracker.announce-flush-interval}")
//    @Transactional
    public void flushAnnounces() {
        boolean locked = announceFlushLock.tryLock();
        if (!locked) {
            log.info("Skipped this round announce flush, another task is running. Remaining announces: {}", announceDeque.size());
            return;
        }
        try {
            if (announceDeque.isEmpty()) {
                return;
            }
            while (!announceDeque.isEmpty()) {
                executeAnnounce();
            }
        } finally {
            announceFlushLock.unlock();
        }
    }

//    @SneakyThrows(value = JsonProcessingException.class)
//    public void executeAnnounce(PeerAnnounce announce) {
//        meterRegistry.counter("sparkle_tracker_trends_peers", List.of(
//                Tag.of("peer_id", PeerUtil.cutPeerId(new String(announce.peerId(), StandardCharsets.ISO_8859_1))),
//                Tag.of("peer_client_name", PeerUtil.cutClientName(announce.userAgent()))
//        )).increment();
//        if (announce.peerEvent() == PeerEvent.STOPPED) {
//            trackedPeerRepository.deleteByPk_PeerIdAndPk_TorrentInfoHash(
//                    ByteUtil.bytesToHex(announce.peerId())
//                    , ByteUtil.bytesToHex(announce.infoHash()));
//        } else {
//            upsertTrackedPeer(
//                    announce.reqIp(),
//                    ByteUtil.bytesToHex(announce.peerId()),
//                    ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)),
//                    announce.peerIp(),
//                    announce.peerPort(),
//                    ByteUtil.bytesToHex(announce.infoHash()),
//                    announce.uploaded(),
//                    announce.downloaded(),
//                    announce.left(),
//                    announce.peerEvent().ordinal(),
//                    ByteUtil.filterUTF8(announce.userAgent()),
//                    OffsetDateTime.now(),
//                    jacksonObjectMapper.writeValueAsString(geoIPManager.geoData(announce.peerIp()))
//            );
//        }
//    }

//    public void upsert(List<PeerAnnounce> rows){
//        String nativeQuery = """
//            INSERT INTO tracker_peers (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, \
//                                       torrent_info_hash, uploaded_offset, downloaded_offset, \
//                                       "left", last_event, user_agent, last_time_seen, peer_geoip) \
//            VALUES (:reqIp, :peerId, :peerIdHumanReadable, :peerIp, :peerPort, :torrentInfoHash, \
//                    :uploadedOffset, :downloadedOffset, :left, :lastEvent, :userAgent, \
//                    :lastTimeSeen, CAST(:peerGeoIP AS jsonb)) \
//            ON CONFLICT (peer_id, torrent_info_hash) DO UPDATE SET \
//            uploaded_offset = EXCLUDED.uploaded_offset, \
//            downloaded_offset = EXCLUDED.downloaded_offset, \
//            "left" = EXCLUDED."left", \
//            last_event = EXCLUDED.last_event, \
//            user_agent = EXCLUDED.user_agent, \
//            last_time_seen = EXCLUDED.last_time_seen, \
//            peer_geoip = CAST(EXCLUDED.peer_geoip AS jsonb)""";
//
//        MapSqlParameterSource[] params = rows.stream().map(announce -> {
//            try {
//                MapSqlParameterSource paramValues = new MapSqlParameterSource();
//                paramValues.addValue("reqIp", announce.reqIp());
//                paramValues.addValue("peerId", ByteUtil.bytesToHex(announce.peerId()));
//                paramValues.addValue("peerIdHumanReadable", ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)));
//                paramValues.addValue("peerIp", announce.peerIp());
//                paramValues.addValue("peerPort", announce.peerPort());
//                paramValues.addValue("torrentInfoHash", ByteUtil.bytesToHex(announce.infoHash()));
//                paramValues.addValue("uploadedOffset", announce.uploaded());
//                paramValues.addValue("downloadedOffset", announce.downloaded());
//                paramValues.addValue("left", announce.left());
//                paramValues.addValue("lastEvent", announce.peerEvent().ordinal());
//                paramValues.addValue("userAgent", ByteUtil.filterUTF8(announce.userAgent()));
//                paramValues.addValue("lastTimeSeen", OffsetDateTime.now());
//                paramValues.addValue(":peerGeoIP", jacksonObjectMapper.writeValueAsString(geoIPManager.geoData(announce.peerIp())));
//                return paramValues;
//            }catch (JsonProcessingException e){
//                return null;
//            }
//        }).filter(Objects::nonNull).toArray(MapSqlParameterSource[]::new);
//
//        jdbcTemplate.batchUpdate(nativeQuery, params);
//
//    }

    @Cacheable(value = {"peers#3000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) throws InterruptedException {
        parallelAnnounce.acquire();
        try {
            peersFetchCounter.increment();
            List<Peer> v4 = new LinkedList<>();
            List<Peer> v6 = new LinkedList<>();
            int seeders = 0;
            int leechers = 0;
            long downloaded = 0;
            for (TrackedPeer peer : trackedPeerRepository.fetchPeersFromTorrent(
                    ByteUtil.bytesToHex(torrentInfoHash), ByteUtil.bytesToHex(peerId), Math.min(numWant, maxPeersReturn))) {
                if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                    v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPk().getPeerId())));
                } else if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                    v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPk().getPeerId())));
                }
                if (peer.getLeft() == 0) {
                    seeders++;
                } else {
                    leechers++;
                }
            }
            return new TrackedPeerList(v4, v6, seeders, leechers, downloaded);
        } finally {
            parallelAnnounce.release();
        }
    }


    @Cacheable(value = {"scrape#60000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) throws InterruptedException {
        parallelAnnounce.acquire();
        try {
            scrapeCounter.increment();
            var seeders = trackedPeerRepository.countByPk_TorrentInfoHashAndLeft(ByteUtil.bytesToHex(torrentInfoHash), 0L);
            var leechers = trackedPeerRepository.countByPk_TorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
            var downloaded = 0L;
            return new ScrapeResponse(seeders, leechers, downloaded);
        } finally {
            parallelAnnounce.release();
        }
    }

    public record TrackerMetrics(
            long activeTorrents,
            long totalTorrents,
            long peers,
            long seeders,
            long leechers,
            long peersHaveUpload,
            long peersNoUpload
    ) {

    }

    public record ScrapeResponse(
            long seeders,
            long leechers,
            long downloaded
    ) implements Serializable {
    }

    public record PeerAnnounce(
            byte[] infoHash,
            byte[] peerId,
            InetAddress reqIp,
            InetAddress peerIp,
            int peerPort,
            long uploaded,
            long downloaded,
            long left,
            PeerEvent peerEvent,
            String userAgent,
            boolean supportCrypto, String key, long corrupt, long redundant,
            String trackerId, long cryptoPort, boolean hide, long azudp, long azhttp,
            boolean azq, String azver, long azup, String azas, String aznp, long numWant) implements Serializable {

    }

    public record TrackedPeerList(
            List<Peer> v4,
            List<Peer> v6,
            long seeders,
            long leechers,
            long downloaded
    ) implements Serializable {
    }

    public record Peer(
            String ip,
            int port,
            byte[] peerId
    ) implements Serializable {
    }
}
