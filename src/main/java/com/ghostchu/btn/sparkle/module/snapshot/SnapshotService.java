package com.ghostchu.btn.sparkle.module.snapshot;

import com.ghostchu.btn.sparkle.module.snapshot.internal.Snapshot;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SnapshotService extends SparklePage{

    private final SnapshotRepository snapshotRepository;
    private final TorrentService torrentService;

    public SnapshotService(SnapshotRepository snapshotRepository, TorrentService torrentService) {
        this.snapshotRepository = snapshotRepository;
        this.torrentService = torrentService;
    }

    @Modifying
    @Transactional
    public Iterable<Snapshot> saveSnapshots(List<Snapshot> snapshotList) {
        return snapshotRepository.saveAll(snapshotList);
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
        return SnapshotDto.builder()
                .id(snapshot.getId())
                .appId(snapshot.getUserApplication().getAppId())
                .submitId(snapshot.getSubmitId())
                .peerIp(snapshot.getPeerId())
                .peerPort(snapshot.getPeerPort())
                .peerId(snapshot.getPeerId())
                .peerClientName(snapshot.getPeerClientName())
                .torrent(torrentService.toDto(snapshot.getTorrent()))
                .fromPeerTraffic(snapshot.getFromPeerTraffic())
                .fromPeerTrafficSpeed(snapshot.getFromPeerTrafficSpeed())
                .toPeerTraffic(snapshot.getToPeerTraffic())
                .toPeerTrafficSpeed(snapshot.getToPeerTrafficSpeed())
                .peerProgress(snapshot.getPeerProgress())
                .downloaderProgress(snapshot.getDownloaderProgress())
                .flags(snapshot.getFlags())
                .build();
    }


}
