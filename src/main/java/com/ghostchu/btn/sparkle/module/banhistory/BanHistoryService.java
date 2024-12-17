package com.ghostchu.btn.sparkle.module.banhistory;

import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import com.ghostchu.btn.sparkle.util.paging.SparklePage;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class BanHistoryService {
    private final TorrentService torrentService;
    private final BanHistoryRepository banHistoryRepository;

    public BanHistoryService(BanHistoryRepository banHistoryRepository,
                             TorrentService torrentService, GeoIPManager geoIPManager) {
        this.banHistoryRepository = banHistoryRepository;
        this.torrentService = torrentService;
//        AtomicInteger count = new AtomicInteger();
//        CompletableFuture.runAsync(() -> {
//            while(true){
//                var list = banHistoryRepository.findByGeoIPIsNull(PageRequest.of(0, 100000));
//                System.out.println("BanHistory: Get "+list.getSize()+" ips");
//                if(list.isEmpty()) {
//                    System.out.println("BanHistory OK!");
//                    break;
//                }
//                var handled =list.stream().parallel().peek(b -> b.setGeoIP(geoIPManager.geoData(b.getPeerIp())))
//                        .toList();
//                System.gc();
//                System.out.println("Mapped "+handled.size()+" records");
//                banHistoryRepository.saveAll(handled);
//                count.addAndGet(handled.size());
//                System.out.println("BanHistory: Already successfully handled "+count.get()+" records, Execute next batch");
//            }
//        });
    }

    @Cacheable(value = "banHistoryMetrics#1800000", key = "#from+'-'+#to")
    public BanHistoryMetrics getMetrics(OffsetDateTime from, OffsetDateTime to) {
        return new BanHistoryMetrics(
                banHistoryRepository.count(),
                banHistoryRepository.countByInsertTimeBetween(from, to)
        );
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
    public void saveBanHistories(List<BanHistory> banHistoryList) {
        banHistoryRepository.saveAll(banHistoryList);
    }

    public SparklePage<BanHistory, BanHistoryDto> queryRecent(PageRequest pageable) {
        var page = banHistoryRepository.findByOrderByInsertTimeDesc(pageable);
        return new SparklePage<>(page, dat -> dat.map(this::toDto));
    }

    public SparklePage<BanHistory, BanHistoryDto> complexQuery(Specification<BanHistory> specification, PageRequest pageable) {
        var page = banHistoryRepository.findAll(specification, pageable);
        return new SparklePage<>(page, dat -> dat.map(this::toDto));
    }

    public record BanHistoryMetrics(
            long total,
            long recent
    ) implements Serializable {
    }
}
