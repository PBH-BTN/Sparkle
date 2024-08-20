package com.ghostchu.btn.sparkle.module.ping;

import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientDiscoveryService;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientIdentity;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnPeerPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnRule;
import com.ghostchu.btn.sparkle.module.rule.RuleDto;
import com.ghostchu.btn.sparkle.module.rule.RuleService;
import com.ghostchu.btn.sparkle.module.snapshot.SnapshotService;
import com.ghostchu.btn.sparkle.module.snapshot.internal.Snapshot;
import com.ghostchu.btn.sparkle.module.torrent.TorrentService;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import com.ghostchu.btn.sparkle.util.ByteUtil;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.PeerUtil;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.*;

@Service
@Data
@Slf4j
public class PingService {
    private final RuleService ruleService;
    private final BanHistoryService banHistoryService;
    private final SnapshotService snapshotService;
    private final TorrentService torrentService;
    private final ClientDiscoveryService clientDiscoveryService;
    @Value("${service.ping.protocol.min-version}")
    private int minProtocolVersion;
    @Value("${service.ping.protocol.max-version}")
    private int maxProtocolVersion;

    @Modifying
    @Transactional
    public long handlePeers(InetAddress submitterIp, UserApplication userApplication, BtnPeerPing ping) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<Snapshot> snapshotList = ping.getPeers().stream()
                .peek(peer -> identitySet.add(new ClientIdentity(ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId())), ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName())))))
                .map(peer -> {
                    try {
                        return Snapshot.builder()
                                .insertTime(now)
                                .populateTime(new Timestamp(ping.getPopulateTime()))
                                .userApplication(userApplication)
                                .submitId(UUID.randomUUID().toString())
                                .peerIp(IPUtil.toInet(peer.getIpAddress()))
                                .peerPort(peer.getPeerPort())
                                .peerId(ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId())))
                                .peerClientName(ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName())))
                                .torrent(torrentService.createOrGetTorrent(peer.getTorrentIdentifier(), peer.getTorrentSize()))
                                .fromPeerTraffic(peer.getDownloaded())
                                .fromPeerTrafficSpeed(peer.getRtDownloadSpeed())
                                .toPeerTraffic(peer.getUploaded())
                                .toPeerTrafficSpeed(peer.getRtUploadSpeed())
                                .peerProgress(peer.getPeerProgress())
                                .downloaderProgress(peer.getDownloaderProgress())
                                .flags(peer.getPeerFlag())
                                .submitterIp(submitterIp)
                                .build();
                    } catch (Exception e) {
                        log.error("[ERROR] [Ping] 无法创建 Snapshot 对象", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        snapshotService.saveSnapshots(snapshotList);
        clientDiscoveryService.handleIdentities(userApplication.getUser(), now, now, identitySet);
        return snapshotList.size();
    }

    @Modifying
    @Transactional
    public long handleBans(InetAddress submitterIp, UserApplication userApplication, BtnBanPing ping) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<BanHistory> banHistoryList = ping.getBans().stream()
                .peek(peer -> identitySet.add(new ClientIdentity(ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeer().getPeerId())), ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getPeer().getClientName())))))
                .map(ban -> {
                    var peer = ban.getPeer();
                    try {
                        return BanHistory.builder()
                                .insertTime(now)
                                .populateTime(new Timestamp(ping.getPopulateTime()))
                                .userApplication(userApplication)
                                .submitId(UUID.randomUUID().toString())
                                .peerIp(IPUtil.toInet(peer.getIpAddress()))
                                .peerPort(peer.getPeerPort())
                                .peerId(ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId())))
                                .peerClientName(ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName())))
                                .torrent(torrentService.createOrGetTorrent(peer.getTorrentIdentifier(), peer.getTorrentSize()))
                                .fromPeerTraffic(peer.getDownloaded())
                                .fromPeerTrafficSpeed(peer.getRtDownloadSpeed())
                                .toPeerTraffic(peer.getUploaded())
                                .toPeerTrafficSpeed(peer.getRtUploadSpeed())
                                .peerProgress(peer.getPeerProgress())
                                .downloaderProgress(peer.getDownloaderProgress())
                                .flags(peer.getPeerFlag())
                                .submitterIp(submitterIp)
                                .btnBan(ban.isBtnBan())
                                .module(ban.getModule())
                                .rule(ban.getRule())
                                .banUniqueId(ban.getBanUniqueId())
                                .build();
                    } catch (Exception e) {
                        log.error("[ERROR] [Ping] 无法创建 BanHistory 对象", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        banHistoryService.saveBanHistories(banHistoryList);
        clientDiscoveryService.handleIdentities(userApplication.getUser(), now, now, identitySet);
        return banHistoryList.size();
    }

    public BtnRule generateBtnRule() {
        List<RuleDto> entities = new ArrayList<>(ruleService.getUnexpiredRules());
        List<InetAddress> untrustedIps = banHistoryService.generateUntrustedIPAddresses();
        entities.addAll(untrustedIps.stream().map(ip -> new RuleDto(
                0L,
                "BTN-自动生成-不受信任IP",
                ip.getHostAddress(),
                "ip",
                System.currentTimeMillis(),
                System.currentTimeMillis()
        )).toList());
        return new BtnRule(entities);
    }
}
