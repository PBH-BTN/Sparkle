package com.ghostchu.btn.sparkle.module.tracker;

import com.ghostchu.btn.sparkle.module.tracker.internal.*;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class TrackerService {

    private final TrackedPeerRepository trackedPeerRepository;
    private final TrackedTaskRepository trackedTaskRepository;

    private final long inactiveInterval;
    private final int maxPeersReturn;


    public TrackerService(TrackedPeerRepository trackedPeerRepository,
                          TrackedTaskRepository trackedTaskRepository,
                          @Value("${service.tracker.inactive-interval}") long inactiveInterval,
                          @Value("${service.tracker.max-peers-return}") int maxPeersReturn) {
        this.trackedPeerRepository = trackedPeerRepository;
        this.trackedTaskRepository = trackedTaskRepository;
        this.inactiveInterval = inactiveInterval;
        this.maxPeersReturn = maxPeersReturn;
    }

    @Scheduled(fixedRateString = "${service.tracker.cleanup-interval}")
    @Transactional
    public void cleanup() {
        var count = trackedPeerRepository.deleteByLastTimeSeenLessThanEqual(new Timestamp(System.currentTimeMillis() - inactiveInterval));
        log.info("已清除 {} 个不活跃的 Peers", count);
    }

    @Async
    public void executeAnnounce(PeerAnnounce announce) {
        var trackedPeer = trackedPeerRepository.findByPeerIpAndPeerIdAndTorrentInfoHash(
                announce.peerIp(),
                ByteUtil.filterUTF8(ByteUtil.bytesToHex(announce.peerId())),
                ByteUtil.bytesToHex(announce.infoHash())
        ).orElse(new TrackedPeer(
                null,
                announce.reqIp(),
                ByteUtil.filterUTF8(ByteUtil.bytesToHex(announce.peerId())),
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
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis())
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
        trackedPeer.setLastTimeSeen(new Timestamp(System.currentTimeMillis()));
        trackedPeer.setLeft(announce.left());
        trackedPeer.setPeerPort(announce.peerPort());
        trackedPeer.setPeerIp(announce.peerIp());
        trackedPeer.setReqIp(announce.reqIp());
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(ByteUtil.bytesToHex(announce.infoHash())).orElse(new TrackedTask(
                null,
                ByteUtil.bytesToHex(announce.infoHash()),
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(System.currentTimeMillis()),
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
        List<Peer> v4 = new ArrayList<>();
        List<Peer> v6 = new ArrayList<>();
        AtomicInteger seeders = new AtomicInteger();
        AtomicInteger leechers = new AtomicInteger();
        long downloaded = 0;
        trackedPeerRepository.fetchPeersFromTorrent(ByteUtil.bytesToHex(torrentInfoHash), Math.min(numWant, maxPeersReturn))
                .forEach(peer -> {
                    if (peer.getPeerIp() instanceof Inet4Address ipv4) {
                        v4.add(new Peer(ipv4.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPeerId())));
                    }
                    if (peer.getPeerIp() instanceof Inet6Address ipv6) {
                        v6.add(new Peer(ipv6.getHostAddress(), peer.getPeerPort(), ByteUtil.hexToByteArray(peer.getPeerId())));
                    }
                    if (peer.getLeft() == 0) {
                        seeders.incrementAndGet();
                    } else {
                        leechers.incrementAndGet();
                    }
                });
        var trackedTask = trackedTaskRepository.findByTorrentInfoHash(ByteUtil.bytesToHex(torrentInfoHash));
        if (trackedTask.isPresent()) {
            downloaded = trackedTask.get().getDownloadedCount();
        }
        return new TrackedPeerList(v4, v6, seeders.get(), leechers.get(), downloaded);
    }



    @Cacheable(value = {"scrape#300000"}, key = "#torrentInfoHash")
    public ScrapeResponse scrape(byte[] torrentInfoHash) {
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
