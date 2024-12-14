package com.ghostchu.btn.sparkle.module.analyse;

import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRuleRepository;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.util.*;
import inet.ipaddr.Address;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyseService {
    private static final String UNTRUSTED_IP = "不受信任 IP 地址";
    private static final String OVER_DOWNLOAD = "BTN 网络 IP 下载量分析";
    private static final String HIGH_RISK_IP = "高风险 IP 地址";
    private static final String HIGH_RISK_IPV6_IDENTITY = "高风险 IPV6 特征";
    private static final String TRACKER_HIGH_RISK = "Tracker 高风险特征";
    private static final String PCB_MODULE_NAME = "com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker";

    @Autowired
    private BanHistoryRepository banHistoryRepository;
    @Autowired
    private IPMerger ipMerger;
    @Value("${analyse.untrustip.offset}")
    private long untrustedIpAddressGenerateOffset;
    @Value("${analyse.untrustip.threshold}")
    private int untrustedIpAddressGenerateThreshold;
    @Value("${analyse.overdownload.offset}")
    private long overDownloadGenerateOffset;
    @Value("${analyse.overdownload.threshold}")
    private double overDownloadGenerateThreshold;
    @Value("${analyse.highriskips.offset}")
    private long highRiskIpsOffset;
    @Value("${analyse.highriskipv6identity.offset}")
    private long highRiskIpv6IdentityOffset;
    @Value("${analyse.ipv6.prefix-length}")
    private int ipv6ConvertToPrefixLength;
    @Value("${analyse.highriskips.traffic-from-peer-less-than}")
    private long trafficFromPeerLessThan;
    @Autowired
    private AnalysedRuleRepository analysedRuleRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private MeterRegistry meterRegistry;
//    @Autowired
//    private RedisTrackedPeerRepository redisTrackedPeerRepository;

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedRateString = "${analyse.untrustip.interval}")
    public void cronUntrustedIPAddresses() throws InterruptedException {
        try {
            DatabaseCare.generateParallel.acquire();
            var startAt = System.currentTimeMillis();
            var list = ipMerger.merge(banHistoryRepository
                            .generateUntrustedIPAddresses(
                                    TimeUtil.toUTC(System.currentTimeMillis() - untrustedIpAddressGenerateOffset),
                                    OffsetDateTime.now(),
                                    untrustedIpAddressGenerateThreshold,
                                    Duration.ofMinutes(30)
                            )
                            .stream()
                            .map(IPUtil::toString)
                            .collect(Collectors.toList()))
                    .stream().map(IPUtil::toIPAddress)
                    .toList();
            var untrustedIps = filterIP(list).stream().map(ip -> new AnalysedRule(null, ip.toString(), UNTRUSTED_IP, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
            analysedRuleRepository.deleteAllByModule(UNTRUSTED_IP);
            meterRegistry.gauge("sparkle_analyse_untrusted_ip_address", Collections.emptyList(), untrustedIps.size());
            analysedRuleRepository.saveAll(untrustedIps);
            log.info("Untrusted IPs: {}, tooked {} ms", untrustedIps.size(), System.currentTimeMillis() - startAt);
        } finally {
            DatabaseCare.generateParallel.release();
        }
    }

    @Transactional
    public List<AnalysedRule> getAnalysedRules() {
        return analysedRuleRepository.findAll();
    }

    public List<AnalysedRule> getUntrustedIPAddresses() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(UNTRUSTED_IP);
    }

    public List<AnalysedRule> getOverDownloadIPAddresses() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(OVER_DOWNLOAD);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedRateString = "${analyse.highriskips.interval}")
    public void cronHighRiskIps() throws InterruptedException {
        try {
            DatabaseCare.generateParallel.acquire();
            var startAt = System.currentTimeMillis();
            Set<IPAddress> list =
                    new HashSet<>(banHistoryRepository
                            .findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(pastTimestamp(highRiskIpsOffset), nowTimestamp(), PCB_MODULE_NAME, "Transmission 2.94")
                            .stream()
                            .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                            .distinct()
                            .toList());
            list.addAll(banHistoryRepository
                    .findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(pastTimestamp(highRiskIpsOffset), nowTimestamp(), PCB_MODULE_NAME, "Transmission 2.93")
                    .stream()
                    .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                    .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                    .distinct()
                    .toList());
            list.addAll(banHistoryRepository
                    .findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(pastTimestamp(highRiskIpsOffset), nowTimestamp(), PCB_MODULE_NAME, "Transmission 3.00")
                    .stream()
                    .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                    .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                    .distinct()
                    .toList());
            list.addAll(banHistoryRepository
                    .findDistinctByInsertTimeBetweenAndModuleAndPeerClientNameLike(pastTimestamp(highRiskIpsOffset), nowTimestamp(), PCB_MODULE_NAME, "aria2/%")
                    .stream()
                    .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                    .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                    .distinct()
                    .toList());
            var highRiskIps = filterIP(ipMerger.merge(list.stream().map(Address::toString).toList()).stream().map(IPUtil::toIPAddress).toList()).stream()
                    .map(ip -> new AnalysedRule(null, ip.toString(), HIGH_RISK_IP, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
            analysedRuleRepository.deleteAllByModule(HIGH_RISK_IP);
            meterRegistry.gauge("sparkle_analyse_high_risk_ips", Collections.emptyList(), highRiskIps.size());
            analysedRuleRepository.saveAll(highRiskIps);
            log.info("High risk IPs: {}, tooked {} ms", highRiskIps.size(), System.currentTimeMillis() - startAt);
        } finally {
            DatabaseCare.generateParallel.release();
        }
    }

    public List<AnalysedRule> getHighRiskIps() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(HIGH_RISK_IP);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedRateString = "${analyse.highriskipv6identity.interval}")
    public void cronHighRiskIPV6Identity() throws InterruptedException {
        try {
            DatabaseCare.generateParallel.acquire();
            var startAt = System.currentTimeMillis();
            Set<IPAddress> list = new HashSet<>();
            banHistoryRepository.findByPeerIp(
                            "%::1",
                            pastTimestamp(highRiskIpv6IdentityOffset),
                            nowTimestamp()
                    )
                    .stream()
                    .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                    .distinct()
                    .sorted()
                    .forEach(list::add);
            banHistoryRepository.findByPeerIp(
                            "%::2",
                            pastTimestamp(highRiskIpv6IdentityOffset),
                            nowTimestamp()
                    )
                    .stream()
                    .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                    .distinct()
                    .sorted()
                    .forEach(list::add);
            var ips = filterIP(list).stream()
                    .filter(Objects::nonNull)
                    .map(ip -> new AnalysedRule(null, ip.toString(), HIGH_RISK_IPV6_IDENTITY, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
            analysedRuleRepository.deleteAllByModule(HIGH_RISK_IPV6_IDENTITY);
            meterRegistry.gauge("sparkle_analyse_high_risk_ipv6_identity", Collections.emptyList(), ips.size());
            analysedRuleRepository.saveAll(ips);
            log.info("High risk IPV6 identity: {}, tooked {} ms", ips.size(), System.currentTimeMillis() - startAt);
        } finally {
            DatabaseCare.generateParallel.release();
        }
    }

    public List<AnalysedRule> getHighRiskIPV6Identity() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(HIGH_RISK_IPV6_IDENTITY);
    }

//    @Transactional
//    @Modifying
//    @Lock(LockModeType.READ)
//    @Scheduled(fixedRateString = "${analyse.trackerunion.interval}")
//    public void cronUpdateTrackerUnion() throws InterruptedException {
//        try{
//            generateParallel.acquire();
//            var startAt = System.currentTimeMillis();
//            var query = entityManager.createNativeQuery("""
//                    SELECT DISTINCT bh.peer_ip, bh.peer_id, bh.peer_client_name, tp.user_agent, tp.peer_id_human_readable
//                    FROM banhistory bh
//                    INNER JOIN (
//                        SELECT DISTINCT ON (peer_ip) *
//                        FROM tracker_peers
//                        ORDER BY peer_ip, last_time_seen DESC
//                    ) tp ON bh.peer_ip = tp.peer_ip
//                    WHERE module LIKE ? AND bh.insert_time >= ?
//                    ORDER BY bh.peer_ip ASC;
//                    """);
//        }finally {
//            generateParallel.release();
//        }
//    }

//    @Transactional
//    @Modifying
//    @Lock(LockModeType.READ)
//    @Scheduled(fixedRateString = "${analyse.trackerhighrisk.interval}")
//    public void cronUpdateTrackerHighRisk() throws InterruptedException {
//        try {
//            DatabaseCare.generateParallel.acquire();
//            var startAt = System.currentTimeMillis();
//            Set<TrackedPeer> peers = new HashSet<>(redisTrackedPeerRepository.scanPeersWithCondition((peer) -> {
//                if (peer.getUserAgent().contains("curl/")) {
//                    return true;
//                }
//                if (peer.getUserAgent().contains("Transmission") && !peer.getPeerId().startsWith("-TR")) {
//                    return true;
//                }
//                return peer.getUserAgent().contains("qBittorrent") && !peer.getPeerId().startsWith("-qB");
//            }));
//            var ipList = ipMerger.merge(peers.stream().map(TrackedPeer::getPeerIp).toList());
//            // 这里不用 PeerIP，因为 PeerIP 可以被用户操纵
//            var ips = filterIP(ipList.stream().map(IPUtil::toIPAddress).toList()).stream()
//                    .filter(Objects::nonNull)
//                    .map(ip -> new AnalysedRule(null, ip.toString(), TRACKER_HIGH_RISK, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
//            analysedRuleRepository.deleteAllByModule(TRACKER_HIGH_RISK);
//            meterRegistry.gauge("sparkle_analyse_tracker_high_risk", Collections.emptyList(), ips.size());
//            analysedRuleRepository.saveAll(ips);
//            log.info("Tracker high risk IPs: {}, tooked {} ms", ips.size(), System.currentTimeMillis() - startAt);
//        } finally {
//            DatabaseCare.generateParallel.release();
//        }
//    }

    public List<AnalysedRule> getTrackerHighRisk() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(TRACKER_HIGH_RISK);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedRateString = "${analyse.overdownload.interval}")
    public void cronUpdateOverDownload() throws InterruptedException {
        try {
            DatabaseCare.generateParallel.acquire();
            var startAt = System.currentTimeMillis();
            var query = entityManager.createNativeQuery("""
                    WITH LatestSnapshots AS (
                        SELECT DISTINCT ON (s.torrent, s.peer_ip, s.user_application)
                            s.id,
                            s.torrent,
                            s.peer_ip,
                            s.user_application,
                            s.to_peer_traffic,
                            s.last_time_seen
                        FROM
                            public.peer_history s
                        WHERE
                            s.last_time_seen >= ? AND s.to_peer_traffic > 0
                        ORDER BY
                            s.torrent, s.peer_ip, s.user_application, s.last_time_seen DESC
                    ),
                    AggregatedUploads AS (
                        SELECT
                            ls.torrent,
                            ls.peer_ip,
                            SUM(ls.to_peer_traffic) AS total_uploaded
                        FROM
                            LatestSnapshots ls
                        GROUP BY
                            ls.torrent, ls.peer_ip
                        HAVING
                            SUM(ls.to_peer_traffic) > 0
                    )
                    SELECT
                        au.torrent,
                        au.peer_ip,
                        au.total_uploaded,
                        t.size,
                        (au.total_uploaded / t.size::float) * 100 AS upload_percentage
                    FROM
                        AggregatedUploads au
                    JOIN
                        public.torrent t ON au.torrent = t.id
                    WHERE
                        t.size::float > 0 AND au.total_uploaded > t.size::float * ?
                    ORDER BY
                        upload_percentage DESC;
                    
                    """);
            query.setParameter(1, new Timestamp(System.currentTimeMillis() - overDownloadGenerateOffset));
            query.setParameter(2, overDownloadGenerateThreshold);
            List<Object[]> queryResult = query.getResultList();
            var ips = ipMerger.merge(queryResult.stream().map(arr -> IPUtil.toString(((InetAddress) arr[1])))
                            .collect(Collectors.toList())).stream().map(i -> new IPAddressString(i).getAddress())
                    .filter(Objects::nonNull)
                    .toList();
            var rules = filterIP(ips).stream()
                    .map(ip -> new AnalysedRule(null, ip.toString(), OVER_DOWNLOAD,
                            "Generated at " + MsgUtil.getNowDateTimeString())).toList();
            analysedRuleRepository.deleteAllByModule(OVER_DOWNLOAD);
            meterRegistry.gauge("sparkle_analyse_over_download_ips", Collections.emptyList(), rules.size());
            analysedRuleRepository.saveAll(rules);
            log.info("Over download IPs: {}, tooked {} ms", rules.size(), System.currentTimeMillis() - startAt);
        } finally {
            DatabaseCare.generateParallel.release();
        }
    }

//
//    @Transactional
//    @Modifying
//    @Lock(LockModeType.READ)
//    @Scheduled(fixedRateString = "${analyse.untrustip.interval}")
//    public void cronUpdateOverDownload() {
//        var query = entityManager.createNativeQuery("""
//                WITH LatestSnapshots AS (
//                    SELECT
//                        s.id,
//                        s.torrent,
//                        s.peer_ip,
//                        s.user_application,
//                        s.to_peer_traffic,
//                        ROW_NUMBER() OVER (PARTITION BY s.torrent, s.peer_ip, s.user_application ORDER BY s.insert_time DESC) AS rn
//                    FROM
//                        snapshot s
//                    WHERE
//                        s.insert_time >= ? AND s.to_peer_traffic > 0
//                ),
//                AggregatedUploads AS (
//                    SELECT
//                        ls.torrent,
//                        ls.peer_ip,
//                        SUM(ls.to_peer_traffic) AS total_uploaded
//                    FROM
//                        LatestSnapshots ls
//                    WHERE
//                        ls.rn = 1
//                    GROUP BY
//                        ls.torrent,
//                        ls.peer_ip
//                    HAVING
//                        SUM(ls.to_peer_traffic) > 0
//                )
//                SELECT
//                    au.torrent,
//                    au.peer_ip,
//                    au.total_uploaded,
//                    t.size,
//                    (au.total_uploaded / t.size::float) AS upload_percentage
//                FROM
//                    AggregatedUploads au
//                JOIN
//                    torrent t ON au.torrent = t.id
//                WHERE
//                    au.total_uploaded > t.size * ?
//                ORDER BY
//                    au.total_uploaded DESC;
//                """);
//        query.setParameter(1, new Timestamp(System.currentTimeMillis() - overDownloadGenerateOffset));
//        query.setParameter(2, overDownloadGenerateThreshold);
//        List<Object[]> queryResult = query.getResultList();
//        var ips = ipMerger.merge(queryResult.stream().map(arr -> IPUtil.toString(((InetAddress) arr[1]))).collect(Collectors.toList()));
//        List<AnalysedRule> rules = new ArrayList<>();
//        for (String ip : ips) {
//            try {
//                if (new IPAddressString(ip).getAddress().isLocal()) {
//                    continue;
//                }
//                rules.add(new AnalysedRule(
//                        null,
//                        ip,
//                        OVER_DOWNLOAD,
//                        "Generated at " + MsgUtil.getNowDateTimeString()
//                ));
//            } catch (Exception ignored) {
//
//            }
//        }
//        analysedRuleRepository.deleteAllByModule(OVER_DOWNLOAD);
//        meterRegistry.gauge("sparkle_analyse_over_download_ips", Collections.emptyList(), rules.size());
//        analysedRuleRepository.saveAll(rules);
//    }

    public Collection<IPAddress> filterIP(Collection<IPAddress> ips) {
        var list = new ArrayList<>(ips);
        return list.stream().filter(ip -> !ip.isLocal() && !ip.isLoopback())
                .map(ip -> {
                    try {
                        if (ip.getPrefixLength() == null && ip.isIPv6()) {
                            ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength).toZeroHost();
                        }
                    } catch (Exception e) {
                        log.error("Unable to convert {} with prefix block {}.", ip, ipv6ConvertToPrefixLength, e);
                    }
                    return ip;
                })
                .distinct()
                .collect(Collectors.toList());
    }


    private OffsetDateTime nowTimestamp() {
        return OffsetDateTime.now();
    }

    private OffsetDateTime pastTimestamp(long offset) {
        return OffsetDateTime.now().minus(offset, ChronoUnit.MILLIS);
    }
}
