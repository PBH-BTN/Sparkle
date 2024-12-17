package com.ghostchu.btn.sparkle.module.ping;

import com.ghostchu.btn.sparkle.module.analyse.AnalyseService;
import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientDiscoveryService;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientIdentity;
import com.ghostchu.btn.sparkle.module.peerhistory.PeerHistoryService;
import com.ghostchu.btn.sparkle.module.peerhistory.internal.PeerHistory;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnPeerHistoryPing;
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
    @Autowired
    private PeerHistoryService peerHistoryService;

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
                                .flags(ByteUtil.filterUTF8(peer.getPeerFlag()))
                                .submitterIp(submitterIp)
                                .btnBan(ban.isBtnBan())
                                .module(ByteUtil.filterUTF8(ban.getModule()))
                                .rule(ByteUtil.filterUTF8(ban.getRule()))
                                .banUniqueId(ByteUtil.filterUTF8(ban.getBanUniqueId()))
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
        List<RuleDto> rules = new LinkedList<>();
        rules.addAll(analyseService.getAnalysedRules()
                .stream()
                .map(rule -> new RuleDto(null, rule.getModule(), rule.getIp(), "ip", 0L, 0L))
                .toList());
        rules.addAll(ruleService.getUnexpiredRules());
        rules.sort(Comparator.comparing(RuleDto::getContent));
        meterRegistry.gauge("sparkle_ping_rules", rules.size());
        return new BtnRule(rules);
    }

    @Modifying
    @Transactional
    public long handlePeerHistories(InetAddress inetAddress, UserApplication userApplication, BtnPeerHistoryPing ping) {
        meterRegistry.counter("sparkle_peer_histories").increment();
        OffsetDateTime now = OffsetDateTime.now();
        var usr = userApplication.getUser();
        usr.setLastAccessAt(now);
        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        if (ping.getPeers().size() > 15000) {
            log.error("[ERROR] [Ping] 一次性处理的 PeerHistory 数量过多: {}，丢弃一部分数据……", ping.getPeers().size());
            ping.setPeers(ping.getPeers().subList(0, 15000));
        }
        List<PeerHistory> snapshotList = ping.getPeers().stream()
                .peek(peer -> identitySet.add(new ClientIdentity(PeerUtil.cutPeerId(peer.getPeerId()), PeerUtil.cutClientName(peer.getClientName()))))
                .map(peer -> {
                    try {
                        return PeerHistory.builder()
                                .insertTime(now)
                                .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
                                .userApplication(userApplication)
                                .submitId(UUID.randomUUID().toString())
                                .peerIp(IPUtil.toInet(peer.getIpAddress()))
                                .peerId(ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId())))
                                .peerClientName(ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName())))
                                .torrent(torrentService.createOrGetTorrent(peer.getTorrentIdentifier(), peer.getTorrentSize()))
                                .fromPeerTraffic(peer.getDownloaded())
                                .fromPeerTrafficOffset(peer.getDownloadedOffset())
                                .toPeerTraffic(peer.getUploaded())
                                .toPeerTrafficOffset(peer.getUploadedOffset())
                                .flags(ByteUtil.filterUTF8(peer.getPeerFlag()))
                                .firstTimeSeen(TimeUtil.toUTC(peer.getFirstTimeSeen().getTime()))
                                .lastTimeSeen(TimeUtil.toUTC(peer.getLastTimeSeen().getTime()))
                                .submitterIp(inetAddress)
                                // .geoIP(geoIPManager.geoData(IPUtil.toInet(peer.getIpAddress())))
                                .build();
                    } catch (Exception e) {
                        log.error("[ERROR] [Ping] 无法创建 History 对象", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        peerHistoryService.saveHistories(snapshotList);
        clientDiscoveryService.handleIdentities(userApplication.getUser(), now, now, identitySet);
        meterRegistry.counter("sparkle_ping_histories_processed").increment(snapshotList.size());
        return snapshotList.size();
    }
}
