package com.ghostchu.btn.sparkle.module.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class TrackerService {

    private final TrackedPeerRepository trackedPeerRepository;
    private final long inactiveInterval;
    private final int maxPeersReturn;
    private final GeoIPManager geoIPManager;
    private final Counter peersFetchCounter;
    private final Counter scrapeCounter;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper jacksonObjectMapper;
    private final Queue<PeerAnnounce> announceDeque;
    private final ReentrantLock announceFlushLock = new ReentrantLock();
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final int maxAnnounceProcessBatchSize;



    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn, GeoIPManager geoIPManager,
                          MeterRegistry meterRegistry, ObjectMapper jacksonObjectMapper,
                          @Value("${service.tracker.announce-queue-max-size}") int maxAnnounceQueueSize,
                          @Value("${service.tracker.announce-process-batch-size}") int maxAnnounceProcessBatchSize,
                          NamedParameterJdbcTemplate jdbcTemplate) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxAnnounceProcessBatchSize = maxAnnounceProcessBatchSize;
        this.announceDeque = new LinkedBlockingQueue<>(maxAnnounceQueueSize);
        this.maxPeersReturn = maxPeersReturn;
        this.geoIPManager = geoIPManager;
        this.meterRegistry = meterRegistry;
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
        this.jacksonObjectMapper = jacksonObjectMapper;
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

    public boolean scheduleAnnounce(PeerAnnounce announce) {
        return announceDeque.offer(announce);
    }


    @Modifying
    @Scheduled(fixedRateString = "${service.tracker.announce-flush-interval}")
    @Transactional
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


                List<PeerAnnounce> batch = new ArrayList<>(maxAnnounceProcessBatchSize);
                // 从队列中取出任务
                while (!announceDeque.isEmpty() && batch.size() < maxAnnounceProcessBatchSize) {
                    PeerAnnounce announce = announceDeque.poll();
                    if (announce != null) {
                        batch.add(announce);
                    }
                }
                if (batch.isEmpty()) {
                    return; // 队列为空，直接返回
                }
                // 按 peerId 和 infoHash 分组，只保留最后一个事件
                Map<byte[], PeerAnnounce> latestAnnounceMap = new HashMap<>();
                for (PeerAnnounce announce : batch) {
                    byte[] key = new byte[announce.peerId().length + announce.infoHash().length];
                    System.arraycopy(announce.peerId(), 0, key, 0, announce.peerId().length);
                    System.arraycopy(announce.infoHash(), 0, key, announce.peerId().length, announce.infoHash().length);
                    latestAnnounceMap.put(key, announce);
                }
                batch.clear();
                // 批量删除 STOPPED
                List<Map<String, Object>> deleteParams = new ArrayList<>();
                for (PeerAnnounce announce : latestAnnounceMap.values().stream().filter(pa -> pa.peerEvent() == PeerEvent.STOPPED).toList()) {
                    Map<String, Object> params = new HashMap<>(2);
                    params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
                    params.put("info_hash", ByteUtil.bytesToHex(announce.infoHash()));
                    deleteParams.add(params);
                }
                if (!deleteParams.isEmpty()) {
                    String deleteSql = "DELETE FROM tracker_peers WHERE peer_id = :peer_id AND info_hash = :info_hash";
                    jdbcTemplate.batchUpdate(deleteSql, deleteParams.toArray(new Map[0]));
                    deleteParams.clear();
                }

                // 批量插入或更新 active announces
                List<Map<String, Object>> upsertParams = new ArrayList<>();
                try {
                    for (PeerAnnounce announce : latestAnnounceMap.values().stream().filter(pa -> pa.peerEvent() != PeerEvent.STOPPED).toList()) {
                        Map<String, Object> params = new HashMap<>(13);
                        params.put("req_ip", announce.reqIp().getHostAddress());
                        params.put("peer_id", ByteUtil.bytesToHex(announce.peerId()));
                        params.put("peer_id_human_readable", ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)));
                        params.put("peer_ip", announce.peerIp().getHostAddress());
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
                        (CAST(:req_ip AS inet), :peer_id, :peer_id_human_readable, CAST(:peer_ip AS inet), :peer_port, :torrent_info_hash, 
                         :uploaded_offset, :downloaded_offset, :left, :last_event, :user_agent, 
                         :last_time_seen, CAST(:peer_geoip AS jsonb))
                    ON CONFLICT (peer_id, torrent_info_hash)
                    DO UPDATE SET 
                        uploaded_offset = EXCLUDED.uploaded_offset,
                        downloaded_offset = EXCLUDED.downloaded_offset,
                        "left" = EXCLUDED."left",
                        last_event = EXCLUDED.last_event,
                        last_time_seen = EXCLUDED.last_time_seen
                """;
                jdbcTemplate.batchUpdate(upsertSql, upsertParams.toArray(new Map[0]));


            }
        } finally {
            announceFlushLock.unlock();
        }
    }

    @Cacheable(value = {"peers#10000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) throws InterruptedException {
        peersFetchCounter.increment();
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (var peer : trackedPeerRepository.fetchPeersFromTorrent(
                ByteUtil.bytesToHex(torrentInfoHash), Math.min(numWant, maxPeersReturn))) {
            if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort()));
            } else if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort()));
            }
            if (peer.getLeft() == 0) {
                seeders++;
            } else {
                leechers++;
            }
        }
        return new TrackedPeerList(v4, v6, seeders, leechers, downloaded);
    }


    @Cacheable(value = {"scrape#60000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) {
        scrapeCounter.increment();
        var seeders = trackedPeerRepository.countByPk_TorrentInfoHashAndLeft(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var leechers = trackedPeerRepository.countByPk_TorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var downloaded = 0L;
        return new ScrapeResponse(seeders, leechers, downloaded);
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
            String ip,
            int port
    ) implements Serializable {
    }
}
