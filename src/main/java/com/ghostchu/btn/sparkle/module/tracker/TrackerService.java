package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.RedisTrackedPeerRepository;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class TrackerService {
    private final Counter peersFetchCounter;
    private final Counter scrapeCounter;
    private final MeterRegistry meterRegistry;
    private final RedisTrackedPeerRepository redisTrackedPeerRepository;
    private final Deque<PeerAnnounce> peerAnnounces;
    private final ReentrantLock announceFlushLock = new ReentrantLock();
    private final int processBatchSize;


    public TrackerService(
            @Value("${service.tracker.announce-queue-max-size}") int queueMaxSize,
            @Value("${service.tracker.announce-process-batch-size}") int processBatchSize,
            MeterRegistry meterRegistry,
            RedisTrackedPeerRepository redisTrackedPeerRepository) {
        this.meterRegistry = meterRegistry;
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
        this.redisTrackedPeerRepository = redisTrackedPeerRepository;
        this.peerAnnounces = new LinkedBlockingDeque<>(queueMaxSize);
        this.processBatchSize = processBatchSize;
    }

    @Scheduled(fixedRateString = "${service.tracker.metrics-interval}")
    @Transactional
    public void updateTrackerMetrics() {
        var uniqueRecords = redisTrackedPeerRepository.countUniqueRecords();
        var totalPeers = meterRegistry.gauge("sparkle_tracker_tracking_total_peers", uniqueRecords.getPeers());
        var uniquePeers = meterRegistry.gauge("sparkle_tracker_tracking_unique_peers", uniqueRecords.getUniquePeerIds());
        var uniqueIps = meterRegistry.gauge("sparkle_tracker_tracking_unique_ips", uniqueRecords.getUniqueIps());
        var activeTasks = meterRegistry.gauge("sparkle_tracker_tracking_active_tasks", uniqueRecords.getUniqueInfoHashes());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}", totalPeers, uniquePeers, uniqueIps, activeTasks);
    }

    @Scheduled(fixedRateString = "${service.tracker.cleanup-interval}")
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
            while (!peerAnnounces.isEmpty()) {
                executeRedisAnnounce();
            }
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
                    announce.left() == 0,
                    announce.userAgent()
            ));
        }
        try {
            redisTrackedPeerRepository.registerPeers(announceMap);
        } catch (Exception e) {
            log.warn("Failed to register peers on Redis", e);

        }
    }

    @Cacheable(value = {"peers#10000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
        peersFetchCounter.increment();
        List<Peer> v4 = new ArrayList<>(100);
        List<Peer> v6 = new ArrayList<>(100);
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (var peer : redisTrackedPeerRepository.getPeers(torrentInfoHash, numWant)) {
            try {
                InetAddress address = InetAddress.getByName(peer.getPeerIp());
                if (address instanceof Inet4Address inet4Address) {
                    v4.add(new Peer(peer.getPeerId().getBytes(StandardCharsets.ISO_8859_1), inet4Address.getHostAddress(), peer.getPeerPort()));
                } else if (address instanceof Inet6Address inet6Address) {
                    v6.add(new Peer(peer.getPeerId().getBytes(StandardCharsets.ISO_8859_1), inet6Address.getHostAddress(), peer.getPeerPort()));
                } else {
                    continue;
                }
                if (peer.isSeeder()) {
                    seeders++;
                } else {
                    leechers++;
                }
            } catch (UnknownHostException e) {
                log.warn("Failed to parse peer IP: {}", peer.getPeerIp());
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
