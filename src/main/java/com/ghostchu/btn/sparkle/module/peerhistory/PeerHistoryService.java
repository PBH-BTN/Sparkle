package com.ghostchu.btn.sparkle.module.peerhistory;

import com.ghostchu.btn.sparkle.module.peerhistory.internal.PeerHistory;
import com.ghostchu.btn.sparkle.module.peerhistory.internal.PeerHistoryRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PeerHistoryService {

    private final PeerHistoryRepository peerHistoryRepository;
    private final TorrentService torrentService;
    @PersistenceContext
    private EntityManager entityManager;

    public PeerHistoryService(PeerHistoryRepository peerHistoryRepository, TorrentService torrentService, GeoIPManager geoIPManager) {
        this.peerHistoryRepository = peerHistoryRepository;
        this.torrentService = torrentService;
//        this.geoIPManager = geoIPManager;
//        AtomicInteger count = new AtomicInteger();
//        CompletableFuture.runAsync(() -> {
//            while (true) {
//                var list = snapshotRepository.findByGeoIPIsNull(PageRequest.of(0, 100000));
//                System.out.println("Snapshot: Get " + list.getSize() + " ips");
//                if (list.isEmpty()) {
//                    System.out.println("Snapshot OK!");
//                    break;
//                }
//                var handled = list.stream().parallel().peek(snapshot -> snapshot.setGeoIP(geoIPManager.geoData(snapshot.getPeerIp())))
//                        .toList();
//                System.gc();
//                System.out.println("Mapped " + handled.size() + " records");
//                snapshotRepository.saveAll(handled);
//                count.addAndGet(handled.size());
//                System.out.println("Snapshot: Already successfully handled " + count.get() + " records, Execute next batch");
//            }
//        });
    }

    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Retryable(retryFor = OptimisticLockingFailureException.class, backoff = @Backoff(delay = 100, multiplier = 2))
    public void saveHistories(List<PeerHistory> peerHistoryList) {
        peerHistoryRepository.saveAll(peerHistoryList);
    }

//
//    @Cacheable(value = "snapshotMetrics#1800000", key = "#from+'-'+#to")
//    public SnapshotMetrics getMetrics(OffsetDateTime from, OffsetDateTime to) {
//        return new SnapshotMetrics(snapshotRepository.count(), snapshotRepository.countByInsertTimeBetween(from, to));
//    }

//    public SparklePage<SnapshotHistory, SnapshotHistoryDto> queryRecent(PageRequest pageable) {
//        var page = snapshotRepository.findByOrderByInsertTimeDesc(pageable);
//        return new SparklePage<>(page, dat -> dat.map(this::toDto));
//    }
//
//    public SparklePage<SnapshotHistory, SnapshotHistoryDto> query(Specification<SnapshotHistory> specification, Pageable pageable) {
//        var page = snapshotRepository.findAll(specification, pageable);
//        return new SparklePage<>(page, ct -> ct.map(this::toDto));
//    }

//    public SnapshotHistoryDto toDto(SnapshotHistory snapshotHistory) {
//        return SnapshotHistoryDto.builder()
//                .id(snapshotHistory.getId())
//                .appId(snapshotHistory.getUserApplication().getAppId())
//                .submitId(snapshotHistory.getSubmitId()).peerIp(snapshotHistory.getPeerIp().getHostAddress())
//                .peerPort(snapshotHistory.getPeerPort()).peerId(snapshotHistory.getPeerId())
//                .peerClientName(snapshotHistory.getPeerClientName())
//                .torrent(torrentService.toDto(snapshotHistory.getTorrent()))
//                .fromPeerTraffic(snapshotHistory.getFromPeerTraffic())
//                .fromPeerTrafficSpeed(snapshotHistory.getFromPeerTrafficSpeed())
//                .toPeerTraffic(snapshotHistory.getToPeerTraffic())
//                .toPeerTrafficSpeed(snapshotHistory.getToPeerTrafficSpeed()).peerProgress(snapshotHistory.getPeerProgress()).downloaderProgress(snapshotHistory.getDownloaderProgress()).flags(snapshotHistory.getFlags()).build();
//    }

//    public record SnapshotOverDownloadResult(
//            long torrentId,
//            InetAddress peerIp,
//            long totalUploaded,
//            long torrentSize,
//            double uploadPercentage
//    ) {
//
//    }
//
//    public record SnapshotMetrics(long total, long recent) implements Serializable {
//    }
}
