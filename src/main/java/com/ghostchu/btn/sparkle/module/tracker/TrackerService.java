package com.ghostchu.btn.sparkle.module.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.PeerRegister;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackerStorage;
import com.ghostchu.btn.sparkle.util.PeerUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class TrackerService {
    // private final TrackedPeerRepository trackedPeerRepository;
    private final TrackerStorage trackerStorage;
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
                          @Value("${service.tracker.max-parallel-announce}") int maxParallelAnnounce,
                          TrackerStorage trackerStorage) {
        //this.trackedPeerRepository = trackedPeerRepository;
        this.trackerStorage = trackerStorage;
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

    @Scheduled(fixedRateString = "${service.tracker.metrics-interval}")
    public void updateTrackerMetrics() {
        var totalPeers = meterRegistry.gauge("sparkle_tracker_tracking_total_peers", trackerStorage.peersCount());
        var uniquePeers = meterRegistry.gauge("sparkle_tracker_tracking_unique_peers", trackerStorage.uniquePeers());
        var uniqueIps = meterRegistry.gauge("sparkle_tracker_tracking_unique_ips", trackerStorage.uniqueIps());
        var activeTasks = meterRegistry.gauge("sparkle_tracker_tracking_active_tasks", trackerStorage.torrentsCount());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}", totalPeers, uniquePeers, uniqueIps, activeTasks);
    }

    @Scheduled(fixedRateString = "${service.tracker.cleanup-interval}")
    public void cleanup() {
        trackerStorage.cleanup();
    }

    public void scheduleAnnounce(PeerAnnounce announce) {
        // announceDeque.offer(announce);
        executeAnnounce(announce);
    }

    @Modifying
    @Scheduled(fixedRateString = "${service.tracker.announce-flush-interval}")
    @Transactional
    public void flushAnnounces() {
        boolean locked = announceFlushLock.tryLock();
        if (!locked) {
            log.info("Skipped this round announce flush, another task is running. ");
            return;
        }
        try {
            if (announceDeque.isEmpty()) {
                return;
            }
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
        }
    }

    //@SneakyThrows(value = JsonProcessingException.class)
    public void executeAnnounce(PeerAnnounce announce) {
        try {
            semaphore.acquire();
            meterRegistry.counter("sparkle_tracker_trends_peers", List.of(
                    Tag.of("peer_id", PeerUtil.cutPeerId(new String(announce.peerId(), StandardCharsets.ISO_8859_1))),
                    Tag.of("peer_client_name", PeerUtil.cutClientName(announce.userAgent()))
            )).increment();
            if (announce.peerEvent() == PeerEvent.STOPPED) {
                trackerStorage.unregisterPeer(announce.infoHash(), announce.peerId());
            } else {
                trackerStorage.announce(
                        announce.infoHash(),
                        announce.reqIp(),
                        announce.peerId(),
                        announce.peerIp(),
                        announce.peerPort(),
                        announce.uploaded(),
                        announce.downloaded(),
                        announce.left(),
                        announce.peerEvent(),
                        announce.userAgent(),
                        System.currentTimeMillis(),
                        (short) announce.numWant()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }

    // @Cacheable(value = {"peers#3000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
        peersFetchCounter.increment();
        List<Peer> v4 = new LinkedList<>();
        List<Peer> v6 = new LinkedList<>();
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (Map.Entry<byte[], PeerRegister> peer : trackerStorage.getPeers(torrentInfoHash).entrySet()) {
            if (peer.getValue().getPeerIp() instanceof Inet4Address ipv4) {
                v4.add(new Peer(ipv4.getHostAddress(), peer.getValue().getPeerPort(), peer.getKey()));
            } else if (peer.getValue().getPeerIp() instanceof Inet6Address ipv6) {
                v6.add(new Peer(ipv6.getHostAddress(), peer.getValue().getPeerPort(), peer.getKey()));
            }
            if (peer.getValue().getLeft() == 0) {
                seeders++;
            } else {
                leechers++;
            }
        }
        return new TrackedPeerList(v4, v6, seeders, leechers, downloaded);
    }


    //  @Cacheable(value = {"scrape#60000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) {
        scrapeCounter.increment();
        long seeders = 0;
        long leechers = 0;
        for (PeerRegister value : trackerStorage.getPeers(torrentInfoHash).values()) {
            if (value.getLeft() == 0) {
                seeders++;
            } else {
                leechers++;
            }
        }
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
            String userAgent,
            boolean supportCrypto, String key, long corrupt, long redundant,
            String trackerId, long cryptoPort, boolean hide,
            //long azudp, long azhttp, boolean azq, String azver, long azup, String azas, String aznp,
            long numWant) implements Serializable {

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
