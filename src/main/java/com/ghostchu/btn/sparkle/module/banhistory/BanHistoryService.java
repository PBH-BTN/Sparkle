package com.ghostchu.btn.sparkle.module.banhistory;

import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.module.userapp.UserApplicationService;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    @Cacheable({"untrustedIpAddress#600000"})
    @Transactional
    @Lock(LockModeType.READ)
    public List<InetAddress> generateUntrustedIPAddresses() {
        return banHistoryRepository.generateUntrustedIPAddresses(new Timestamp(System.currentTimeMillis() - untrustedIpAddressGenerateOffset), new Timestamp(System.currentTimeMillis()), untrustedIpAddressGenerateThreshold);
    }

    public BanHistoryDto toDto(BanHistory banHistory) {
        return BanHistoryDto.builder()
                .id(banHistory.getId())
                .appId(banHistory.getUserApplication().getAppId())
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
                .btnBan(banHistory.getBtnBan())
                .module(banHistory.getModule())
                .rule(banHistory.getRule())
                .banUniqueId(banHistory.getBanUniqueId())
                .build();
    }

    @Modifying
    @Transactional
    public Iterable<BanHistory> saveBanHistories(List<BanHistory> banHistoryList) {
        return banHistoryRepository.saveAll(banHistoryList);
    }

    public SparklePage<BanHistory, BanHistoryDto> queryRecent(PageRequest pageable) {
        var page = banHistoryRepository.findByOrderByInsertTimeDesc(pageable);
        return new SparklePage<>(page, dat -> dat.map(this::toDto));
    }

    public SparklePage<BanHistory, BanHistoryDto> complexQuery(Specification<BanHistory> specification, PageRequest pageable) {
        var page = banHistoryRepository.findAll(specification, pageable);
        return new SparklePage<>(page, dat -> dat.map(this::toDto));
    }
}
