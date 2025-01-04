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
import com.google.common.hash.BloomFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
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
    @Async
    public void handlePeers(InetAddress submitterIp, UserApplication userApplication, BtnPeerPing ping) {
        meterRegistry.counter("sparkle_ping_peers").increment();
        OffsetDateTime now = OffsetDateTime.now();
//        var usr = userApplication.getUser();
//        usr.setLastAccessAt(now);
//        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<Snapshot> snapshotList = new ArrayList<>();
        long processed = 0;
        var it = ping.getPeers().iterator();
        var submitId = UUID.randomUUID().toString();
        while (it.hasNext()) {
            var peer = it.next();
            var peerId = ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId()));
            var peerClientName = ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName()));
            if (peerClientName.length() > 250) {
                // get the first 64 chars and append "..." and append last 64 chars
                peerClientName = peerClientName.substring(0, 64) + "[...]" + peerClientName.substring(peerClientName.length() - 64);
            }
            snapshotList.add(Snapshot.builder()
                    .insertTime(now)
                    .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
                    .userApplication(userApplication)
                    .submitId(submitId)
                    .peerIp(IPUtil.toInet(peer.getIpAddress()))
                    .peerPort(peer.getPeerPort())
                    .peerId(peerId)
                    .peerClientName(peerClientName)
                    .torrent(torrentService.createOrGetTorrent(peer.getTorrentIdentifier(), peer.getTorrentSize()))
                    .fromPeerTraffic(peer.getDownloaded())
                    .fromPeerTrafficSpeed(peer.getRtDownloadSpeed())
                    .toPeerTraffic(peer.getUploaded())
                    .toPeerTrafficSpeed(peer.getRtUploadSpeed())
                    .peerProgress(peer.getPeerProgress())
                    .downloaderProgress(peer.getDownloaderProgress())
                    .flags(peer.getPeerFlag())
                    .submitterIp(submitterIp)
                    .build());
            identitySet.add(new ClientIdentity(peerId, peerClientName));
            it.remove();
            if (identitySet.size() >= 5000 || snapshotList.size() >= 5000) {
                snapshotService.saveSnapshots(snapshotList);
                clientDiscoveryService.handleIdentities(now, now, identitySet);
                meterRegistry.counter("sparkle_ping_peers_processed").increment(snapshotList.size());
                processed += snapshotList.size();
                snapshotList.clear();
                identitySet.clear();
            }
        }
        snapshotService.saveSnapshots(snapshotList);
        meterRegistry.counter("sparkle_ping_peers_processed").increment(snapshotList.size());
        clientDiscoveryService.handleIdentities(now, now, identitySet);
        processed += snapshotList.size();
    }

    @Modifying
    @Transactional
    @Async
    public void handleBans(InetAddress submitterIp, UserApplication userApplication, BtnBanPing ping) {
        meterRegistry.counter("sparkle_ping_bans").increment();
        OffsetDateTime now = OffsetDateTime.now();
//        var usr = userApplication.getUser();
//        usr.setLastAccessAt(now);
//        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<BanHistory> banHistoryList = new ArrayList<>();
        long processed = 0;
        var it = ping.getBans().iterator();
        var submitId = UUID.randomUUID().toString();
        while (it.hasNext()) {
            var ban = it.next();
            var peer = ban.getPeer();
            var peerId = ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId()));
            var peerClientName = ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName()));
            if (peerClientName.length() > 250) {
                peerClientName = peerClientName.substring(0, 64) + "[...]" + peerClientName.substring(peerClientName.length() - 64);
            }
            var rule = ban.getRule();
            if (rule.length() > 250) {
                rule = rule.substring(0, 64) + "[...]" + rule.substring(rule.length() - 64);
            }
            banHistoryList.add(BanHistory.builder()
                    .insertTime(now)
                    .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
                    .userApplication(userApplication)
                    .submitId(submitId)
                    .peerIp(IPUtil.toInet(peer.getIpAddress()))
                    .peerPort(peer.getPeerPort())
                    .peerId(peerId)
                    .peerClientName(peerClientName)
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
                    .rule(ByteUtil.filterUTF8(rule))
                    .banUniqueId(ByteUtil.filterUTF8(ban.getBanUniqueId()))
                    .geoIP(geoIPManager.geoData(IPUtil.toInet(peer.getIpAddress())))
                    .build());
            identitySet.add(new ClientIdentity(peerId, peerClientName));
            it.remove();
            if (identitySet.size() >= 5000 || banHistoryList.size() >= 5000) {
                banHistoryService.saveBanHistories(banHistoryList);
                clientDiscoveryService.handleIdentities(now, now, identitySet);
                meterRegistry.counter("sparkle_ping_bans_processed").increment(banHistoryList.size());
                processed += banHistoryList.size();
                banHistoryList.clear();
                identitySet.clear();
            }
        }
        banHistoryService.saveBanHistories(banHistoryList);
        meterRegistry.counter("sparkle_ping_bans_processed").increment(banHistoryList.size());
        clientDiscoveryService.handleIdentities(now, now, identitySet);
        processed += banHistoryList.size();
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
//        var usr = userApplication.getUser();
//        usr.setLastAccessAt(now);
//        userService.saveUser(usr);
        Set<ClientIdentity> identitySet = new HashSet<>();
        List<PeerHistory> peerHistoryList = new ArrayList<>();
        long processed = 0;
        var it = ping.getPeers().iterator();
        var submitId = UUID.randomUUID().toString();
        BloomFilter<String> bloomFilter = BloomFilter.create((from, into) -> into.putString(from, StandardCharsets.ISO_8859_1), 10_000_000, 0.01);
        while (it.hasNext()) {
            var peer = it.next();
            var peerId = ByteUtil.filterUTF8(PeerUtil.cutPeerId(peer.getPeerId()));
            var peerClientName = ByteUtil.filterUTF8(PeerUtil.cutClientName(peer.getClientName()));
            if (peerClientName.length() > 250) {
                // get the first 64 chars and append "..." and append last 64 chars
                peerClientName = peerClientName.substring(0, 64) + "[...]" + peerClientName.substring(peerClientName.length() - 64);
            }
            peerHistoryList.add(PeerHistory.builder()
                    .insertTime(now)
                    .populateTime(TimeUtil.toUTC(ping.getPopulateTime()))
                    .userApplication(userApplication)
                    .submitId(submitId)
                    .peerIp(IPUtil.toInet(peer.getIpAddress()))
                    .peerId(peerId)
                    .peerClientName(peerClientName)
                    .torrent(torrentService.createOrGetTorrent(peer.getTorrentIdentifier(), peer.getTorrentSize()))
                    .fromPeerTraffic(peer.getDownloaded())
                    .fromPeerTrafficOffset(peer.getDownloadedOffset())
                    .toPeerTraffic(peer.getUploaded())
                    .toPeerTrafficOffset(peer.getUploadedOffset())
                    .flags(ByteUtil.filterUTF8(peer.getPeerFlag()))
                    .firstTimeSeen(TimeUtil.toUTC(peer.getFirstTimeSeen().getTime()))
                    .lastTimeSeen(TimeUtil.toUTC(peer.getLastTimeSeen().getTime()))
                    .submitterIp(inetAddress)
                    .build());
            if (bloomFilter.put(peerId + "@@@" + peerClientName)) {
                identitySet.add(new ClientIdentity(peerId, peerClientName));
            }
            // 避免爆内存，必须及时清理
            it.remove();
            if (peerHistoryList.size() >= 5000) {
                peerHistoryService.saveHistories(peerHistoryList);
                meterRegistry.counter("sparkle_ping_histories_processed").increment(peerHistoryList.size());
                processed += peerHistoryList.size();
                peerHistoryList.clear();
            }

            if (identitySet.size() > 5000) {
                clientDiscoveryService.handleIdentities(now, now, identitySet);
                identitySet.clear();
            }
        }
        peerHistoryService.saveHistories(peerHistoryList);
        clientDiscoveryService.handleIdentities(now, now, identitySet);
        meterRegistry.counter("sparkle_ping_histories_processed").increment(peerHistoryList.size());
        processed += peerHistoryList.size();
        return processed;
    }
}
