package com.ghostchu.btn.sparkle.snapshot;

import com.ghostchu.btn.sparkle.snapshot.internal.Snapshot;
import com.ghostchu.btn.sparkle.snapshot.internal.SnapshotRepository;
import com.ghostchu.btn.sparkle.torrent.TorrentService;
import com.ghostchu.btn.sparkle.userapp.UserApplicationService;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SnapshotService {

    private final SnapshotRepository snapshotRepository;
    private final UserApplicationService userApplicationService;
    private final TorrentService torrentService;

    public SnapshotService(SnapshotRepository snapshotRepository, UserApplicationService userApplicationService, TorrentService torrentService) {
        this.snapshotRepository = snapshotRepository;
        this.userApplicationService = userApplicationService;
        this.torrentService = torrentService;
    }

    public Iterable<Snapshot> saveSnapshots(List<Snapshot> snapshotList) {
        return snapshotRepository.saveAll(snapshotList);
    }

    public SparklePage<List<SnapshotDto>> query(Specification<Snapshot> specification, Pageable pageable){
       var page =  snapshotRepository.findAll(specification,pageable);
       return new SparklePage<>(page.getPageable().getPageNumber()
               , page.getPageable().getPageSize()
               , page.getTotalElements(),page.getContent().stream().map(this::toDto).toList());
    }

    public SnapshotDto toDto(Snapshot snapshot) {
        return SnapshotDto.builder()
                .id(snapshot.getId())
                .userApplication(userApplicationService.toDto(snapshot.getUserApplication()))
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
                .submitterIp(snapshot.getSubmitterIp().getHostAddress())
                .build();
    }
}
