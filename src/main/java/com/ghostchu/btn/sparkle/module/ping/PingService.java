package com.ghostchu.btn.sparkle.module.ping;

import com.ghostchu.btn.sparkle.module.analyse.AnalyseService;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
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
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import com.ghostchu.btn.sparkle.util.*;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Data
@Slf4j
public class PingService {
    private final RuleService ruleService;
    private final BanHistoryService banHistoryService;
    private final SnapshotService snapshotService;
    private final TorrentService torrentService;
    private final ClientDiscoveryService clientDiscoveryService;
    private final AnalyseService analyseService;
    private final UserService userService;
    private final GeoIPManager geoIPManager;
    @Autowired
    private IPMerger iPMerger;
    @Value("${service.ping.protocol.min-version}")
    private int minProtocolVersion;
    @Value("${service.ping.protocol.max-version}")
    private int maxProtocolVersion;
    @Autowired
    private MeterRegistry meterRegistry;

    @Modifying
    @Transactional
    public long handlePeers(InetAddress submitterIp, UserApplication userApplication, BtnPeerPing ping) {
        meterRegistry.counter("sparkle_ping_peers").increment();
        OffsetDateTime now = OffsetDateTime.now();
        var usr = userApplication.getUser();
        usr.setLastAccessAt(now);
        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<Snapshot> snapshotList = ping.getPeers().stream()
                .peek(peer -> identitySet.add(new ClientIdentity(PeerUtil.cutPeerId(peer.getPeerId()), PeerUtil.cutClientName(peer.getClientName()))))
                .map(peer -> {
                    try {
                        return Snapshot.builder()
                                .insertTime(now)
                                .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
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
                                // .geoIP(geoIPManager.geoData(IPUtil.toInet(peer.getIpAddress())))
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
        meterRegistry.counter("sparkle_ping_peers_processed").increment(snapshotList.size());
        return snapshotList.size();
    }

    @Modifying
    @Transactional
    public long handleBans(InetAddress submitterIp, UserApplication userApplication, BtnBanPing ping) {
        meterRegistry.counter("sparkle_ping_bans").increment();
        OffsetDateTime now = OffsetDateTime.now();
        var usr = userApplication.getUser();
        usr.setLastAccessAt(now);
        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<BanHistory> banHistoryList = ping.getBans().stream()
                .peek(peer -> identitySet.add(new ClientIdentity(PeerUtil.cutPeerId(peer.getPeer().getPeerId()), PeerUtil.cutClientName(peer.getPeer().getClientName()))))
                .map(ban -> {
                    var peer = ban.getPeer();
                    try {
                        return BanHistory.builder()
                                .insertTime(now)
                                .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
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
                                .geoIP(geoIPManager.geoData(IPUtil.toInet(peer.getIpAddress())))
                                .build();
                    } catch (Exception e) {
                        log.error("[ERROR] [Ping] 无法创建 BanHistory 对象", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        banHistoryService.saveBanHistories(banHistoryList);
        meterRegistry.counter("sparkle_ping_bans_processed").increment(banHistoryList.size());
        clientDiscoveryService.handleIdentities(userApplication.getUser(), now, now, identitySet);
        return banHistoryList.size();
    }

    @Cacheable({"btnRule#60000"})
    public BtnRule generateBtnRule() {
        List<String> analysedRules = new ArrayList<>(analyseService.getUntrustedIPAddresses().stream()
                .map(AnalysedRule::getIp).toList());
//        analysedRules.addAll(analyseService.getOverDownloadIPAddresses().stream()
//                .map(AnalysedRule::getIp).toList());
//        analysedRules.addAll(analyseService.getHighRiskIps().stream()
//                .map(AnalysedRule::getIp).toList());
//        analysedRules.addAll(analyseService.getHighRiskIPV6Identity().stream()
//                .map(AnalysedRule::getIp).toList());
        var ipsMerged = iPMerger.merge(analysedRules.stream().distinct().sorted().collect(Collectors.toList()));
        meterRegistry.gauge("sparkle_ping_rules", ipsMerged.size());
        return new BtnRule(ipsMerged.stream().map(ip -> new RuleDto(null, "合并规则", ip, "ip", 0L, 0L)).toList());
    }
}
