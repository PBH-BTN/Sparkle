package com.ghostchu.btn.sparkle.banhistory;

import com.ghostchu.btn.sparkle.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.torrent.TorrentService;
import com.ghostchu.btn.sparkle.userapp.UserApplicationService;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.List;

@Service
public class BanHistoryService {
    private final UserApplicationService userApplicationService;
    private final TorrentService torrentService;
    private final BanHistoryRepository banHistoryRepository;


    @Value("${service.banhistory.untrustipgenerate.offset}")
    private long untrustedIpAddressGenerateOffset;
    @Value("${service.banhistory.untrustipgenerate.threshold}")
    private int untrustedIpAddressGenerateThreshold;

    public BanHistoryService(BanHistoryRepository banHistoryRepository, UserApplicationService userApplicationService, TorrentService torrentService) {
        this.banHistoryRepository = banHistoryRepository;
        this.userApplicationService = userApplicationService;
        this.torrentService = torrentService;
    }

    @Transactional
    @Lock(LockModeType.READ)
    public List<InetAddress> generateUntrustedIPAddresses() {
        return banHistoryRepository.generateUntrustedIPAddresses(new Timestamp(System.currentTimeMillis() - untrustedIpAddressGenerateOffset), new Timestamp(System.currentTimeMillis()), untrustedIpAddressGenerateThreshold);
    }

    public BanHistoryDto toDto(BanHistory banHistory) {
        return BanHistoryDto.builder()
                .id(banHistory.getId())
                .userApplication(userApplicationService.toDto(banHistory.getUserApplication()))
                .submitId(banHistory.getSubmitId())
                .peerIp(banHistory.getPeerIp().getHostAddress())
                .peerPort(banHistory.getPeerPort())
                .peerId(banHistory.getPeerId())
                .peerClientName(banHistory.getPeerClientName())
                .torrent(torrentService.toDto(banHistory.getTorrent()))
                .fromPeerTraffic(banHistory.getFromPeerTraffic())
                .fromPeerTrafficSpeed(banHistory.getFromPeerTrafficSpeed())
                .toPeerTraffic(banHistory.getToPeerTraffic())
                .toPeerTrafficSpeed(banHistory.getToPeerTrafficSpeed())
                .peerProgress(banHistory.getPeerProgress())
                .downloaderProgress(banHistory.getDownloaderProgress())
                .flags(banHistory.getFlags())
                .submitterIp(banHistory.getSubmitterIp().getHostAddress())
                .build();
    }

    public Iterable<BanHistory> saveBanHistories(List<BanHistory> banHistoryList) {
        return banHistoryRepository.saveAll(banHistoryList);
    }

    public SparklePage<List<BanHistoryDto>> query(Specification<BanHistory> specification, PageRequest pageable) {
        var page =  banHistoryRepository.findAll(specification,pageable);
        return new SparklePage<>(page.getPageable().getPageNumber()
                , page.getPageable().getPageSize()
                , page.getTotalElements(),page.getContent().stream().map(this::toDto).toList());
    }
}
