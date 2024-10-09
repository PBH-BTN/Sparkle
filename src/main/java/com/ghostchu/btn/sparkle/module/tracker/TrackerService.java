package com.ghostchu.btn.sparkle.module.tracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.PeerUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final Semaphore semaphore;
    private final Deque<PeerAnnounce> announceDeque = new ConcurrentLinkedDeque<>();
    private final ReentrantLock announceFlushLock = new ReentrantLock();


    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn, GeoIPManager geoIPManager,
                          MeterRegistry meterRegistry, ObjectMapper jacksonObjectMapper,
                          @Value("${service.tracker.max-parallel-announce}") int maxParallelAnnounce) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
        this.geoIPManager = geoIPManager;
        this.meterRegistry = meterRegistry;
        this.announceCounter = meterRegistry.counter("sparkle_tracker_announce");
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.semaphore = new Semaphore(maxParallelAnnounce);
    }

    @Scheduled(fixedDelayString = "${service.tracker.metrics-interval}")
    @Transactional
    public void updateTrackerMetrics() {
        var totalPeers = meterRegistry.gauge("sparkle_tracker_tracking_total_peers", trackedPeerRepository.count());
        var uniquePeers = meterRegistry.gauge("sparkle_tracker_tracking_unique_peers", trackedPeerRepository.countDistinctPeerIdBy());
        var uniqueIps = meterRegistry.gauge("sparkle_tracker_tracking_unique_ips", trackedPeerRepository.countDistinctPeerIpBy());
        var activeTasks = meterRegistry.gauge("sparkle_tracker_tracking_active_tasks", trackedPeerRepository.countDistinctTorrentInfoHashBy());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}", totalPeers, uniquePeers, uniqueIps, activeTasks);
    }

    @Scheduled(fixedDelayString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = trackedPeerRepository.deleteByLastTimeSeenLessThanEqual(TimeUtil.toUTC(System.currentTimeMillis() - inactiveInterval));
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    public void scheduleAnnounce(PeerAnnounce announce) {
        announceDeque.offer(announce);
    }

    @Modifying
    @Transactional
    @Scheduled(fixedDelayString = "${service.tracker.announce-flush-interval}")
    public void flushAnnounces() {
        boolean locked = announceFlushLock.tryLock();
        if (!locked) {
            log.info("Skipped this round announce flush, another task is running. ");
        }
        if (!announceDeque.isEmpty()) {
            log.info("开始保存 Peers");
        }
        try {
            try (ExecutorService flushService = Executors.newVirtualThreadPerTaskExecutor()) {
                while (true) {
                    var announce = announceDeque.poll();
                    if (announce == null) break;
                    flushService.submit(() -> {
                        try {
                            executeAnnounce(announce);
                        } catch (Exception e) {
                            log.warn("Unable to process the announce {}, skipping...", announce, e);
                        }
                    });
                }
            }
        } finally {
            announceFlushLock.unlock();
            log.info("Peers 保存完毕");
        }
    }

    @SneakyThrows(value = JsonProcessingException.class)
    public void executeAnnounce(PeerAnnounce announce) {
        try {
            semaphore.acquire();
            meterRegistry.counter("sparkle_tracker_trends_peers", List.of(
                    Tag.of("peer_id", PeerUtil.cutPeerId(new String(announce.peerId(), StandardCharsets.ISO_8859_1))),
                    Tag.of("peer_client_name", PeerUtil.cutClientName(announce.userAgent()))
            )).increment();
            if (announce.peerEvent() == PeerEvent.STOPPED) {
                trackedPeerRepository.deleteByPk_PeerIdAndPk_TorrentInfoHash(
                        ByteUtil.bytesToHex(announce.peerId())
                        , ByteUtil.bytesToHex(announce.infoHash()));
            } else {
                trackedPeerRepository.upsertTrackedPeer(
                        announce.reqIp(),
                        ByteUtil.bytesToHex(announce.peerId()),
                        ByteUtil.filterUTF8(new String(announce.peerId(), StandardCharsets.ISO_8859_1)),
                        announce.peerIp(),
                        announce.peerPort(),
                        ByteUtil.bytesToHex(announce.infoHash()),
                        announce.uploaded(),
                        announce.downloaded(),
                        announce.left(),
                        announce.peerEvent().ordinal(),
                        ByteUtil.filterUTF8(announce.userAgent()),
                        OffsetDateTime.now(),
                        jacksonObjectMapper.writeValueAsString(geoIPManager.geoData(announce.peerIp()))
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Cacheable(value = {"peers#3000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
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
    }


    @Cacheable(value = {"scrape#60000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) {
        scrapeCounter.increment();
        var seeders = trackedPeerRepository.countByPk_TorrentInfoHashAndLeft(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var leechers = trackedPeerRepository.countByPk_TorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var downloaded = 0L;
        return new ScrapeResponse(seeders, leechers, downloaded);
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
            String userAgent
    ) implements Serializable {

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
