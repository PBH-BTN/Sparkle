package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.PeerEvent;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerId;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private final EntityManagerFactory entityManagerFactory;

    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn, GeoIPManager geoIPManager,
                          MeterRegistry meterRegistry,
                          EntityManagerFactory entityManagerFactory) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
        this.geoIPManager = geoIPManager;
        this.meterRegistry = meterRegistry;
        this.entityManagerFactory = entityManagerFactory;
        this.announceCounter = meterRegistry.counter("sparkle_tracker_announce");
        this.peersFetchCounter = meterRegistry.counter("sparkle_tracker_peers_fetch");
        this.scrapeCounter = meterRegistry.counter("sparkle_tracker_scrape");
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
        count += trackedPeerRepository.deleteByLastEvent(PeerEvent.STOPPED);
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    @Async
    @Transactional
    @Modifying
    public void executeAnnounce(List<PeerAnnounce> announces) {
        announceCounter.increment(announces.size());
        List<TrackedPeer> trackedPeers = new ArrayList<>();
        for (PeerAnnounce announce : announces) {
            trackedPeers.add(new TrackedPeer(
                    new TrackedPeerId(ByteUtil.filterUTF8(ByteUtil.bytesToHex(announce.peerId())), ByteUtil.bytesToHex(announce.infoHash())),
                    announce.reqIp(),
                    ByteUtil.filterUTF8(new String(announce.peerId, StandardCharsets.ISO_8859_1)),
                    announce.peerIp(),
                    announce.peerPort(),
                    announce.uploaded(),
                    announce.uploaded(),
                    announce.downloaded(),
                    announce.downloaded(),
                    announce.left(),
                    announce.peerEvent(),
                    announce.userAgent(),
                    OffsetDateTime.now(),
                    geoIPManager.geoData(announce.peerIp()),
                    geoIPManager.geoData(announce.reqIp()),
                    0
            ));
        }
        trackedPeerRepository.saveAll(trackedPeers);
    }

    @Cacheable(value = {"peers#3000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
        peersFetchCounter.increment();
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (TrackedPeer peer : trackedPeerRepository.fetchPeersFromTorrent(ByteUtil.bytesToHex(torrentInfoHash),
                ByteUtil.bytesToHex(peerId),
                PeerEvent.STOPPED,
                Math.min(numWant, maxPeersReturn))) {
            if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getId().getPeerId())));
            }
            if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getId().getPeerId())));
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
        var seeders = trackedPeerRepository.countById_TorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var leechers = trackedPeerRepository.countById_TorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var downloaded = 0L;
        return new ScrapeResponse(seeders, leechers, downloaded);
    }

//    public record TrackerMetrics(
//            long activeTorrents,
//            long totalTorrents,
//            long peers,
//            long seeders,
//            long leechers,
//            long peersHaveUpload,
//            long peersNoUpload
//    ) {
//
//    }

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
