package com.ghostchu.btn.sparkle.module.snapshot;

import com.ghostchu.btn.sparkle.module.snapshot.internal.Snapshot;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.List;

@Service
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final TorrentService torrentService;
    @PersistenceContext
    private EntityManager entityManager;

    public SnapshotService(SnapshotRepository snapshotRepository, TorrentService torrentService, GeoIPManager geoIPManager) {
        this.snapshotRepository = snapshotRepository;
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

    @Modifying
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Async
    public void saveSnapshots(List<Snapshot> snapshotList) {
        snapshotRepository.saveAll(snapshotList);
    }


    @Cacheable(value = "snapshotMetrics#1800000", key = "#from+'-'+#to")
    public SnapshotMetrics getMetrics(Timestamp from, Timestamp to) {
        return new SnapshotMetrics(snapshotRepository.count(), snapshotRepository.countByInsertTimeBetween(from, to));
    }

    public SparklePage<Snapshot, SnapshotDto> queryRecent(PageRequest pageable) {
        var page = snapshotRepository.findByOrderByInsertTimeDesc(pageable);
        return new SparklePage<>(page, dat -> dat.map(this::toDto));
    }

    public SparklePage<Snapshot, SnapshotDto> query(Specification<Snapshot> specification, Pageable pageable) {
        var page = snapshotRepository.findAll(specification, pageable);
        return new SparklePage<>(page, ct -> ct.map(this::toDto));
    }

    public SnapshotDto toDto(Snapshot snapshot) {
        return SnapshotDto.builder().id(snapshot.getId()).appId(snapshot.getUserApplication().getAppId()).submitId(snapshot.getSubmitId()).peerIp(snapshot.getPeerId()).peerPort(snapshot.getPeerPort()).peerId(snapshot.getPeerId()).peerClientName(snapshot.getPeerClientName()).torrent(torrentService.toDto(snapshot.getTorrent())).fromPeerTraffic(snapshot.getFromPeerTraffic()).fromPeerTrafficSpeed(snapshot.getFromPeerTrafficSpeed()).toPeerTraffic(snapshot.getToPeerTraffic()).toPeerTrafficSpeed(snapshot.getToPeerTrafficSpeed()).peerProgress(snapshot.getPeerProgress()).downloaderProgress(snapshot.getDownloaderProgress()).flags(snapshot.getFlags()).build();
    }

    public record SnapshotOverDownloadResult(
            long torrentId,
            InetAddress peerIp,
            long totalUploaded,
            long torrentSize,
            double uploadPercentage
    ) {

    }

    public record SnapshotMetrics(long total, long recent) implements Serializable {
    }
}
