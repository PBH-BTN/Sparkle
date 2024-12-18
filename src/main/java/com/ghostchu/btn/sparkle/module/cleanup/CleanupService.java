package com.ghostchu.btn.sparkle.module.cleanup;

import com.ghostchu.btn.sparkle.module.audit.impl.AuditRepository;
import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscovery;
import com.ghostchu.btn.sparkle.module.clientdiscovery.internal.ClientDiscoveryRepository;
import com.ghostchu.btn.sparkle.module.peerhistory.internal.PeerHistoryRepository;
import com.ghostchu.btn.sparkle.module.snapshot.internal.SnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@Slf4j
public class CleanupService {
    private final PeerHistoryRepository peerHistoryRepository;
    private final SnapshotRepository snapshotRepository;
    private final AuditRepository auditRepository;
    private final ClientDiscoveryRepository clientDiscoveryRepository;

    public CleanupService(PeerHistoryRepository peerHistoryRepository, SnapshotRepository snapshotRepository, AuditRepository auditRepository, ClientDiscoveryRepository clientDiscoveryRepository) {
        this.peerHistoryRepository = peerHistoryRepository;
        this.snapshotRepository = snapshotRepository;
        this.auditRepository = auditRepository;
        this.clientDiscoveryRepository = clientDiscoveryRepository;
    }

    // 每天凌晨 3 点清理
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        var deletedHistories = peerHistoryRepository.deleteAllByInsertTimeBefore(OffsetDateTime.now().minusDays(14));
        log.info("[清理] 删除了 14 天前的 PeerHistory 共 {} 条", deletedHistories);
        var deletedSnapshots = snapshotRepository.deleteAllByInsertTimeBefore(OffsetDateTime.now().minusDays(7));
        log.info("[清理] 删除了 7 天前的 Snapshot 共 {} 条", deletedSnapshots);
        var deletedAudits = auditRepository.deleteAllByTimestampBefore(OffsetDateTime.now().minusDays(30));
        log.info("[清理] 删除了 30 天前的 Audit 共 {} 条", deletedAudits);
        var deletedDiscoveries = clientDiscoveryRepository.delete((Specification<ClientDiscovery>) (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(root.get("clientName"), "FDM%"),
                criteriaBuilder.like(root.get("clientName"), "FD6%"),
                criteriaBuilder.like(root.get("clientName"), "Free Download Manager%"),
                criteriaBuilder.like(root.get("clientName"), "Xunlei%"),
                criteriaBuilder.like(root.get("clientName"), "XunLei%"),
                criteriaBuilder.like(root.get("clientName"), "-XL00%"),
                criteriaBuilder.like(root.get("clientName"), "aria2/%"),
                criteriaBuilder.like(root.get("clientName"), "MG-%"),
                criteriaBuilder.like(root.get("peerId"), "FDM%"),
                criteriaBuilder.like(root.get("peerId"), "FD6%"),
                criteriaBuilder.like(root.get("peerId"), "-XL00%"),
                criteriaBuilder.like(root.get("peerId"), "A2-"),
                criteriaBuilder.like(root.get("peerId"), "MG-%")
        ));
        log.info("[清理] 删除了垃圾客户端发现共 {} 条", deletedDiscoveries);
    }
}
