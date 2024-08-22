package com.ghostchu.btn.sparkle.module.snapshot;

import com.ghostchu.btn.sparkle.module.snapshot.internal.Snapshot;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final TorrentService torrentService;
    @PersistenceContext
    private EntityManager entityManager;

    public SnapshotService(SnapshotRepository snapshotRepository, TorrentService torrentService) {
        this.snapshotRepository = snapshotRepository;
        this.torrentService = torrentService;
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

    @Scheduled(fixedDelay = 3000)
    public List<SnapshotOverDownloadResult> complexCompute() {
        var query = entityManager.createNativeQuery("""
                WITH LatestSnapshots AS (
                    SELECT
                        s.id,
                        s.torrent,
                        s.peer_ip,
                        s.user_application,
                        s.to_peer_traffic,
                        ROW_NUMBER() OVER (PARTITION BY s.torrent, s.peer_ip, s.user_application ORDER BY s.insert_time DESC) AS rn
                    FROM
                        public.snapshot s
                    WHERE
                        s.insert_time >= NOW() - INTERVAL '14 days'
                ),
                AggregatedUploads AS (
                    SELECT
                        ls.torrent,
                        ls.peer_ip,
                        SUM(ls.to_peer_traffic) AS total_uploaded
                    FROM
                        LatestSnapshots ls
                    WHERE
                        ls.rn = 1
                    GROUP BY
                        ls.torrent,
                        ls.peer_ip
                    HAVING
                        SUM(ls.to_peer_traffic) > 0
                )
                SELECT
                    au.torrent,
                    au.peer_ip,
                    au.total_uploaded,
                    t.size,
                    (au.total_uploaded / t.size::float) * 100 AS upload_percentage
                FROM
                    AggregatedUploads au
                JOIN
                    public.torrent t ON au.torrent = t.id
                WHERE
                    au.total_uploaded > t.size * 1.5
                ORDER BY
                    au.total_uploaded DESC;

                """);
//        List<?> results = query.getResultList();
//        System.out.println(results.getFirst().getClass());
//        System.out.println("ok!");
        return null;
    }

    public SnapshotDto toDto(Snapshot snapshot) {
        return SnapshotDto.builder().id(snapshot.getId()).appId(snapshot.getUserApplication().getAppId()).submitId(snapshot.getSubmitId()).peerIp(snapshot.getPeerId()).peerPort(snapshot.getPeerPort()).peerId(snapshot.getPeerId()).peerClientName(snapshot.getPeerClientName()).torrent(torrentService.toDto(snapshot.getTorrent())).fromPeerTraffic(snapshot.getFromPeerTraffic()).fromPeerTrafficSpeed(snapshot.getFromPeerTrafficSpeed()).toPeerTraffic(snapshot.getToPeerTraffic()).toPeerTrafficSpeed(snapshot.getToPeerTrafficSpeed()).peerProgress(snapshot.getPeerProgress()).downloaderProgress(snapshot.getDownloaderProgress()).flags(snapshot.getFlags()).build();
    }

    public record SnapshotOverDownloadResult(
            long torrentId,
            InetAddress peerIp,
            long totalUploaded,
            long torrentSize,
            float uploadPercentage
    ) {

    }

    public record SnapshotMetrics(long total, long recent) {
    }
}
