package com.ghostchu.btn.sparkle.module;

import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscoveryRepository;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Controller
public class IndexController extends SparkleController {
    private final BanHistoryRepository banHistoryRepository;
    private final SnapshotRepository snapshotRepository;
    private final ClientDiscoveryRepository clientDiscoveryRepository;
    private final TrackedPeerRepository trackedPeerRepository;

    public IndexController(BanHistoryRepository banHistoryRepository, SnapshotRepository snapshotRepository,
                           ClientDiscoveryRepository clientDiscoveryRepository, TrackedPeerRepository trackedPeerRepository) {
        super();
        this.banHistoryRepository = banHistoryRepository;
        this.snapshotRepository = snapshotRepository;
        this.clientDiscoveryRepository = clientDiscoveryRepository;
        this.trackedPeerRepository = trackedPeerRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        Timestamp daysAgo = new Timestamp(LocalDateTime.now().minusDays(14).atOffset(ZoneOffset.UTC).toInstant().toEpochMilli());
        Timestamp timeNow = new Timestamp(System.currentTimeMillis());
        var metrics = new IndexBtnMetrics(
                banHistoryRepository.count(),
                snapshotRepository.count(),
                14,
                banHistoryRepository.countByInsertTimeBetween(daysAgo, timeNow),
                snapshotRepository.countByInsertTimeBetween(daysAgo, timeNow),
                clientDiscoveryRepository.count(),
                clientDiscoveryRepository.countByFoundAtBetween(daysAgo, timeNow)
        );
        model.addAttribute("btnMetrics", metrics);
        var trackerMetrics = new IndexTrackerMetrics(
                trackedPeerRepository.countTrackingTorrents(),
                trackedPeerRepository.count(),
                trackedPeerRepository.countByLeft(0L),
                trackedPeerRepository.countByLeftNot(0L),
                trackedPeerRepository.countUsersWhoUploadedAnyData(),
                trackedPeerRepository.countUsersWhoDidntUploadAnyData()
        );
        model.addAttribute("trackerMetrics", trackerMetrics);
        return "index";
    }

    public record IndexTrackerMetrics(
        long torrents,
        long peers,
        long seeders,
        long leechers,
        long haveUploadPeers,
        long noUploadPeers
    ){}

    public record IndexBtnMetrics(
            long allTimeBans,
            long allTimeSubmits,
            long rangeInterval,
            long rangeBans,
            long rangeSubmits,
            long allTimeClientDiscovery,
            long rangeClientDiscovery
    ){}
}
