package com.ghostchu.btn.sparkle.module.cleanup;

import com.ghostchu.btn.sparkle.module.peerhistory.internal.PeerHistoryRepository;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class CleanupService {
    private final PeerHistoryRepository peerHistoryRepository;
    private final SnapshotRepository snapshotRepository;

    public CleanupService(PeerHistoryRepository peerHistoryRepository, SnapshotRepository snapshotRepository) {
        this.peerHistoryRepository = peerHistoryRepository;
        this.snapshotRepository = snapshotRepository;
    }

    // 每天凌晨 3 点清理
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        // 14 天前
        var deletedHistories = peerHistoryRepository.deleteByInsertTimeBefore(OffsetDateTime.now().minusDays(14));
        log.info("[清理] 删除了 14 天前的 PeerHistory 共 {} 条", deletedHistories);
        // 7 天前
        var deletedSnapshots = snapshotRepository.deleteByInsertTimeBefore(OffsetDateTime.now().minusDays(7));
        log.info("[清理] 删除了 7 天前的 Snapshot 共 {} 条", deletedSnapshots);
    }
}
