package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.*;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TrackerService {

    private final TrackedPeerRepository trackedPeerRepository;
    private final TrackedTaskRepository trackedTaskRepository;

    private final long inactiveInterval;
    private final int maxPeersReturn;
    private final GeoIPManager geoIPManager;
    private final Counter announceCounter;
    private final Counter peersFetchCounter;
    private final Counter scrapeCounter;
    private final MeterRegistry meterRegistry;

    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          TrackedTaskRepository trackedTaskRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn, GeoIPManager geoIPManager,
                          MeterRegistry meterRegistry) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.trackedTaskRepository = trackedTaskRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
        this.geoIPManager = geoIPManager;
        this.meterRegistry = meterRegistry;
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
        var totalTasks = meterRegistry.gauge("sparkle_tracker_tracking_total_tasks", trackedTaskRepository.count());
        log.info("[Tracker 实时] 总Peer: {}, 唯一Peer: {}, 唯一IP: {}, 活动种子: {}, 种子总数: {}", totalPeers, uniquePeers, uniqueIps, activeTasks, totalTasks);
    }

    @Scheduled(fixedDelayString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = trackedPeerRepository.deleteByLastTimeSeenLessThanEqual(TimeUtil.toUTC(System.currentTimeMillis() - inactiveInterval));
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    @Async
    @Transactional
    @Modifying
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public void executeAnnounce(PeerAnnounce announce) {
        announceCounter.increment();
        var trackedPeer = trackedPeerRepository.findByPeerIpAndPeerIdAndTorrentInfoHash(
                announce.peerIp(),
                ByteUtil.filterUTF8(ByteUtil.bytesToHex(announce.peerId())),
                ByteUtil.bytesToHex(announce.infoHash())
        ).orElse(new TrackedPeer(
                null,
                announce.reqIp(),
                ByteUtil.filterUTF8(ByteUtil.bytesToHex(announce.peerId())),
                ByteUtil.filterUTF8(new String(announce.peerId, StandardCharsets.ISO_8859_1)),
                announce.peerIp(),
                announce.peerPort(),
                ByteUtil.bytesToHex(announce.infoHash()),
                announce.uploaded(),
                announce.uploaded(),
                announce.downloaded(),
                announce.downloaded(),
                announce.left(),
                announce.peerEvent(),
                announce.userAgent(),
                TimeUtil.toUTC(System.currentTimeMillis()),
                TimeUtil.toUTC(System.currentTimeMillis()),
                geoIPManager.geoData(announce.peerIp()),
                geoIPManager.geoData(announce.reqIp())
        ));
        if (trackedPeer.getDownloadedOffset() > announce.downloaded()
                || trackedPeer.getUploadedOffset() > announce.uploaded()) {
            trackedPeer.setDownloaded(trackedPeer.getDownloaded() + announce.downloaded());
            trackedPeer.setUploaded(trackedPeer.getUploaded() + announce.uploaded());
        } else {
            var downloadIncrease = announce.downloaded() - trackedPeer.getDownloaded();
            var uploadedIncrease = announce.uploaded() - trackedPeer.getUploaded();
            trackedPeer.setDownloaded(trackedPeer.getDownloaded() + downloadIncrease);
            trackedPeer.setUploaded(trackedPeer.getUploaded() + uploadedIncrease);
        }
        trackedPeer.setDownloadedOffset(announce.downloaded());
        trackedPeer.setUploadedOffset(announce.uploaded());
        trackedPeer.setUserAgent(announce.userAgent());
        trackedPeer.setLastTimeSeen(TimeUtil.toUTC(System.currentTimeMillis()));
        trackedPeer.setLeft(announce.left());
        trackedPeer.setPeerPort(announce.peerPort());
        trackedPeer.setPeerIp(announce.peerIp());
        trackedPeer.setReqIp(announce.reqIp());
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(ByteUtil.bytesToHex(announce.infoHash())).orElse(new TrackedTask(
                null,
                ByteUtil.bytesToHex(announce.infoHash()),
                TimeUtil.toUTC(System.currentTimeMillis()),
                TimeUtil.toUTC(System.currentTimeMillis()),
                0L, 0L
        ));
        // 检查 task 属性
        if (announce.peerEvent() == PeerEvent.STARTED) {
            // 新 task
            trackedTask.setLeechCount(trackedTask.getLeechCount() + 1);
        }
        if (announce.peerEvent() == PeerEvent.COMPLETED) {
            trackedTask.setDownloadedCount(trackedTask.getDownloadedCount() + 1);
        }
        trackedTaskRepository.save(trackedTask);
        if (announce.peerEvent() == PeerEvent.STOPPED) {
            if (trackedPeer.getId() != null) {
                trackedPeerRepository.delete(trackedPeer);
            }
        } else {
            trackedPeerRepository.save(trackedPeer);
        }
    }

    @Cacheable(value = {"peers#3000"}, key = "#torrentInfoHash")
    public TrackedPeerList fetchPeersFromTorrent(byte[] torrentInfoHash, byte[] peerId, InetAddress peerIp, int numWant) {
        peersFetchCounter.increment();
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        int seeders = 0;
        int leechers = 0;
        long downloaded = 0;
        for (TrackedPeer peer : trackedPeerRepository.fetchPeersFromTorrent(ByteUtil.bytesToHex(torrentInfoHash), ByteUtil.bytesToHex(peerId), Math.min(numWant, maxPeersReturn))) {
            if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPeerId())));
            }
            if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPeerId())));
            }
            if (peer.getLeft() == 0) {
                seeders++;
            } else {
                leechers++;
            }
        }
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(ByteUtil.bytesToHex(torrentInfoHash));
        if (trackedTask.isPresent()) {
            downloaded = trackedTask.get().getDownloadedCount();
        }
        return new TrackedPeerList(v4, v6, seeders, leechers, downloaded);
    }


    @Cacheable(value = {"scrape#60000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) {
        scrapeCounter.increment();
        var seeders = trackedPeerRepository.countByTorrentInfoHashAndLeft(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var leechers = trackedPeerRepository.countByTorrentInfoHashAndLeftNot(ByteUtil.bytesToHex(torrentInfoHash), 0L);
        var downloaded = 0L;
        var optional = trackedTaskRepository.findByTorrentInfoHash(ByteUtil.bytesToHex(torrentInfoHash));
        if (optional.isPresent()) {
            downloaded = optional.get().getDownloadedCount();
        }
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
