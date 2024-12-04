package com.ghostchu.btn.sparkle.module;

import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientDiscoveryService;
import com.ghostchu.btn.sparkle.module.snapshot.SnapshotService;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.module.user.internal.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Controller
public class IndexController extends SparkleController {
    private final BanHistoryService banHistoryService;
    private final SnapshotService snapshotService;
    private final ClientDiscoveryService clientDiscoveryService;
    private final UserService userService;

    public IndexController(BanHistoryService banHistoryService, SnapshotService snapshotService, ClientDiscoveryService clientDiscoveryService, UserService userService, UserRepository userRepository) {
        super();
        this.banHistoryService = banHistoryService;
        this.snapshotService = snapshotService;
        this.clientDiscoveryService = clientDiscoveryService;
        this.userService = userService;
    }

    @GetMapping("/healthcheck")
    public String healthCheck() {
        return "OK";
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!StpUtil.isLogin()) {
            return "redirect:/auth/oauth2/github/login";
        }
//        Timestamp daysAgo = new Timestamp(LocalDateTime.now().minusDays(14).atOffset(ZoneOffset.UTC).toInstant().toEpochMilli());
//        Timestamp timeNow = new Timestamp(System.currentTimeMillis());
//        model.addAttribute("btnMetrics", btnMetrics(daysAgo, timeNow));
//        var trackerMetrics = new IndexTrackerMetrics(
//                trackedPeerRepository.countTrackingTorrents(),
//                trackedPeerRepository.count(),
//                trackedPeerRepository.countByLeft(0L),
//                trackedPeerRepository.countByLeftNot(0L),
//                trackedPeerRepository.countUsersWhoUploadedAnyData(),
//                trackedPeerRepository.countUsersWhoDidntUploadAnyData()
//        );
//        model.addAttribute("trackerMetrics", trackerMetrics);
        model.addAttribute("user", userService.getUser(StpUtil.getLoginIdAsLong()).get());
        return "index";
    }

    public IndexBtnMetrics btnMetrics(OffsetDateTime from, OffsetDateTime to) {
        var banHistory = banHistoryService.getMetrics(from, to);
        var snapshot = snapshotService.getMetrics(from, to);
        var clientDiscovery = clientDiscoveryService.getMetrics(from, to);
        return new IndexBtnMetrics(
                banHistory.total(),
                snapshot.total(),
                14,
                banHistory.recent(),
                snapshot.recent(),
                clientDiscovery.total(),
                clientDiscovery.recent()
        );
    }

    public record IndexTrackerMetrics(
            long torrents,
            long peers,
            long seeders,
            long leechers,
            long haveUploadPeers,
            long noUploadPeers
    ) implements Serializable {
    }

    public record IndexBtnMetrics(
            long allTimeBans,
            long allTimeSubmits,
            long rangeInterval,
            long rangeBans,
            long rangeSubmits,
            long allTimeClientDiscovery,
            long rangeClientDiscovery
    ) implements Serializable {
    }
}