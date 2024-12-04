package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.RedisTrackedPeerRepository;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import inet.ipaddr.IPAddress;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class TrackerService {

    private final int maxPeersReturn;
    private final Counter peersFetchCounter;
    private final Counter scrapeCounter;
    private final MeterRegistry meterRegistry;
    private final RedisTrackedPeerRepository redisTrackedPeerRepository;
    private final GeoIPManager geoIPManager;
    private final Deque<PeerAnnounce> peerAnnounces;
    private final ReentrantLock announceFlushLock = new ReentrantLock();
    private final int processBatchSize;


    public TrackerService(@Value("${service.tracker.max-peers-return}") int maxPeersReturn,
                          @Value("${service.tracker.announce-queue-max-size}") int queueMaxSize,
                          @Value("${service.tracker.announce-process-batch-size}") int processBatchSize,
                          MeterRegistry meterRegistry,
                          RedisTrackedPeerRepository redisTrackedPeerRepository,
                          GeoIPManager geoIPManager) {
        this.maxPeersReturn = maxPeersReturn;
        this.meterRegistry = meterRegistry;
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
        this.redisTrackedPeerRepository = redisTrackedPeerRepository;
        this.peerAnnounces = new LinkedBlockingDeque<>(queueMaxSize);
        this.processBatchSize = processBatchSize;
        this.geoIPManager = geoIPManager;
    }

    @Scheduled(fixedRateString = "${service.tracker.metrics-interval}")
    @Transactional
    public void updateTrackerMetrics() {
        var totalPeers = meterRegistry.gauge("sparkle_tracker_tracking_total_peers", redisTrackedPeerRepository.countPeers());
        var uniquePeers = meterRegistry.gauge("sparkle_tracker_tracking_unique_peers", redisTrackedPeerRepository.countUniquePeerIds());
        var uniqueIps = meterRegistry.gauge("sparkle_tracker_tracking_unique_ips", redisTrackedPeerRepository.countUniqueIps());
        var activeTasks = meterRegistry.gauge("sparkle_tracker_tracking_active_tasks", redisTrackedPeerRepository.countUniqueTorrents());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}", totalPeers, uniquePeers, uniqueIps, activeTasks);
    }

    @Scheduled(fixedRateString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = redisTrackedPeerRepository.cleanup();
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    public boolean scheduleAnnounce(PeerAnnounce announce) {
        return peerAnnounces.offer(announce);
    }


    @Scheduled(fixedRateString = "${service.tracker.announce-flush-interval}")
    public void flushAnnounces() {
        boolean locked = announceFlushLock.tryLock();
        if (!locked) {
            log.info("Skipped this round announce flush, another task is running. Remaining announces: {}", peerAnnounces.size());
            return;
        }
        try {
            if (peerAnnounces.isEmpty()) {
                return;
            }
            executeRedisAnnounce();
        } finally {
            announceFlushLock.unlock();
        }
    }

    private void executeRedisAnnounce() {
        Map<byte[], Set<TrackedPeer>> announceMap = new HashMap<>();
        for (int i = 0; i < processBatchSize && !peerAnnounces.isEmpty(); i++) {
            var announce = peerAnnounces.poll();
            // get or create Set by info_hash
            var peers = announceMap.computeIfAbsent(announce.infoHash(), k -> new HashSet<>());
            peers.add(new TrackedPeer(
                    new String(announce.peerId(), StandardCharsets.ISO_8859_1),
                    announce.reqIp().getHostAddress(),
                    announce.peerIp().getHostAddress(),
                    announce.peerPort(),
                    announce.uploaded(),
                    announce.downloaded(),
                    announce.left(),
                    announce.peerEvent(),
                    announce.userAgent(),
                    OffsetDateTime.now(),
                    geoIPManager.geoData(announce.peerIp())
            ));
        }
        Semaphore semaphore = new Semaphore(4);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var entry : announceMap.entrySet()) {
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        redisTrackedPeerRepository.registerPeers(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        log.warn("Failed to register peers on Redis", e);
                    } finally {
                        semaphore.release();
                    }
                });
            }
        }
    }
//
//    private void executeJdbcAnnounce() {
//
//
//        List<PeerAnnounce> batch = new ArrayList<>(maxAnnounceProcessBatchSize);
//        // 从队列中取出任务
//        while (!announceDeque.isEmpty() && batch.size() < maxAnnounceProcessBatchSize) {
//            PeerAnnounce announce = announceDeque.poll();
//            if (announce != null) {
//                batch.add(announce);
//            }
//        }
//        if (batch.isEmpty()) {
//            return; // 队列为空，直接返回
//        }
//        // 按 peerId 和 infoHash 分组，只保留最后一个事件
//        Map<byte[], PeerAnnounce> latestAnnounceMap = new HashMap<>();
//        for (PeerAnnounce announce : batch) {
//            byte[] key = new byte[announce.peerId().length + announce.infoHash().length];
//            System.arraycopy(announce.peerId(), 0, key, 0, announce.peerId().length);
//            System.arraycopy(announce.infoHash(), 0, key, announce.peerId().length, announce.infoHash().length);
//            latestAnnounceMap.put(key, announce);
//        }
//        batch.clear();
//        // 批量删除 STOPPED
//        List<Map<String, Object>> deleteParams = new ArrayList<>();
//        for (PeerAnnounce announce : latestAnnounceMap.values().stream().filter(pa -> pa.peerEvent() == PeerEvent.STOPPED).toList()) {
//            Map<String, Object> params = new HashMap<>(2);
//            params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
//            params.put("info_hash", ByteUtil.bytesToHex(announce.infoHash()));
//            deleteParams.add(params);
//        }
//        if (!deleteParams.isEmpty()) {
//            String deleteSql = "DELETE FROM tracker_peers WHERE peer_id = :peer_id AND info_hash = :info_hash";
//            jdbcTemplate.batchUpdate(deleteSql, deleteParams.toArray(new Map[0]));
//            deleteParams.clear();
//        }
//
//        // 批量插入或更新 active announces
//        List<Map<String, Object>> upsertParams = new ArrayList<>();
//        try {
//            for (PeerAnnounce announce : latestAnnounceMap.values().stream().filter(pa -> pa.peerEvent() != PeerEvent.STOPPED).toList()) {
//                Map<String, Object> params = new HashMap<>(13);
//                params.put("req_ip", announce.reqIp().getHostAddress());
//                params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
//                params.put("peer_id_human_readable", ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)));
//                params.put("peer_ip", announce.peerIp().getHostAddress());
//                params.put("peer_port", announce.peerPort());
//                params.put("torrent_info_hash", ByteUtil.bytesToHex(announce.infoHash()));
//                params.put("uploaded_offset", announce.uploaded());
//                params.put("downloaded_offset", announce.downloaded());
//                params.put("left", announce.left());
//                params.put("last_event", announce.peerEvent().ordinal());
//                params.put("user_agent", ByteUtil.filterUTF8(announce.userAgent()));
//                params.put("last_time_seen", OffsetDateTime.now());
//                params.put("peer_geoip", jacksonObjectMapper.writeValueAsString(geoIPManager.geoData(announce.peerIp())));
//                upsertParams.add(params);
//            }
//
//        } catch (Exception e) {
//            log.warn("Failed to handle active announce", e);
//        }
//
//        String upsertSql = """
//                    INSERT INTO tracker_peers
//                        (req_ip, peer_id, peer_id_human_readable, peer_ip, peer_port, torrent_info_hash,
//                         uploaded_offset, downloaded_offset, "left", last_event, user_agent,
//                         last_time_seen, peer_geoip)
//                    VALUES
//                        (CAST(:req_ip AS inet), :peer_id, :peer_id_human_readable, CAST(:peer_ip AS inet), :peer_port, :torrent_info_hash,
//                         :uploaded_offset, :downloaded_offset, :left, :last_event, :user_agent,
//                         :last_time_seen, CAST(:peer_geoip AS jsonb))
//                    ON CONFLICT (peer_id, torrent_info_hash)
//                    DO UPDATE SET
//                        uploaded_offset = EXCLUDED.uploaded_offset,
//                        downloaded_offset = EXCLUDED.downloaded_offset,
//                        "left" = EXCLUDED."left",
//                        last_event = EXCLUDED.last_event,
//                        last_time_seen = EXCLUDED.last_time_seen
//                """;
//        jdbcTemplate.batchUpdate(upsertSql, upsertParams.toArray(new Map[0]));
//
//
//    }

    //@Cacheable(value = {"peers#10000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) throws InterruptedException {
        peersFetchCounter.increment();
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (var peer : redisTrackedPeerRepository.getPeers(torrentInfoHash, Math.min(numWant, maxPeersReturn))) {
            IPAddress ipAddress = IPUtil.toIPAddress(peer.getPeerIp());
            if (ipAddress.isIPv4Convertible()) {
                v4.add(new Peer(peer.getPeerId().getBytes(StandardCharsets.ISO_8859_1), ipAddress.toIPv4().toString(), peer.getPeerPort()));
            } else if (ipAddress.isIPv6Convertible()) {
                v6.add(new Peer(peer.getPeerId().getBytes(StandardCharsets.ISO_8859_1), ipAddress.toIPv6().toString(), peer.getPeerPort()));
            }
            if (peer.getLeft() == 0) {
                seeders++;
            } else {
                leechers++;
            }
        }
        return new TrackedPeerList(v4, v6, seeders, leechers, downloaded);
    }


    public ScrapeResponse scrape(byte[] torrentInfoHash) {
        scrapeCounter.increment();
        var scrape = redisTrackedPeerRepository.scrapeTorrent(torrentInfoHash);
        var downloaded = 0L;
        return new ScrapeResponse(scrape.getOrDefault("seeders", 0), scrape.getOrDefault("leechers", 0), downloaded);
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
            String userAgent) implements Serializable {

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
            byte[] peerId,
            String ip,
            int port
    ) implements Serializable {
    }
}
