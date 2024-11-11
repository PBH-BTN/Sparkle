package com.ghostchu.btn.sparkle.module.analyse;

import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRuleRepository;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.util.IPMerger;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.MsgUtil;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyseService {
    private static final String UNTRUSTED_IP = "不受信任 IP 地址";
    private static final String OVER_DOWNLOAD = "BTN 网络 IP 下载量分析";
    private static final String HIGH_RISK_IP = "高风险 IP 地址";
    private static final String HIGH_RISK_IPV6_IDENTITY = "高风险 IPV6 特征";
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

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedDelayString = "${analyse.untrustip.interval}")
    public void cronUntrustedIPAddresses() {
        var list = ipMerger.merge(banHistoryRepository
                        .generateUntrustedIPAddresses(
                                TimeUtil.toUTC(System.currentTimeMillis() - untrustedIpAddressGenerateOffset),
                                OffsetDateTime.now(),
                                untrustedIpAddressGenerateThreshold,
                                "30 minutes"
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
    }

    @Transactional
    public List<AnalysedRule> getAnalysedRules() {
        return analysedRuleRepository.findAll();
    }

    public List<AnalysedRule> getUntrustedIPAddresses() {
        return analysedRuleRepository.findByModule(UNTRUSTED_IP);
    }

    public List<AnalysedRule> getOverDownloadIPAddresses() {
        return analysedRuleRepository.findByModule(OVER_DOWNLOAD);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedDelayString = "${analyse.highriskips.interval}")
    public void cronHighRiskIps() {
        Set<IPAddress> list =
                new HashSet<>(banHistoryRepository
                        .findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("Transmission 2.94", "%ProgressCheatBlocker%", pastTimestamp(highRiskIpsOffset), nowTimestamp())
                        .stream()
                        .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                        .distinct()
                        .toList());
        list.addAll(banHistoryRepository
                .findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("Transmission 2.93", "%ProgressCheatBlocker%", pastTimestamp(highRiskIpsOffset), nowTimestamp())
                .stream()
                .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                .distinct()
                .toList());
        list.addAll(banHistoryRepository
                .findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("Transmission 3.00", "%ProgressCheatBlocker%", pastTimestamp(highRiskIpsOffset), nowTimestamp())
                .stream()
                .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                .distinct()
                .toList());
        list.addAll(banHistoryRepository
                .findDistinctByPeerClientNameAndModuleLikeAndInsertTimeBetween("aria2/%", "%ProgressCheatBlocker%", pastTimestamp(highRiskIpsOffset), nowTimestamp())
                .stream()
                .filter(banHistory -> banHistory.getFromPeerTraffic() != -1 && banHistory.getFromPeerTraffic() < trafficFromPeerLessThan)
                .map(ban -> IPUtil.toIPAddress(ban.getPeerIp().getHostAddress()))
                .distinct()
                .toList());
        var highRiskIps = filterIP(list).stream()
                .map(ip -> new AnalysedRule(null, ip.toString(), HIGH_RISK_IP, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
        analysedRuleRepository.deleteAllByModule(HIGH_RISK_IP);
        meterRegistry.gauge("sparkle_analyse_high_risk_ips", Collections.emptyList(), highRiskIps.size());
        analysedRuleRepository.saveAll(highRiskIps);
    }

    public List<AnalysedRule> getHighRiskIps() {
        return analysedRuleRepository.findByModule(HIGH_RISK_IP);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedDelayString = "${analyse.highriskipv6identity.interval}")
    public void cronHighRiskIPV6Identity() {
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
    }

    public List<AnalysedRule> getHighRiskIPV6Identity() {
        return analysedRuleRepository.findByModule(HIGH_RISK_IPV6_IDENTITY);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedDelayString = "${analyse.overdownload.interval}")
    public void cronUpdateOverDownload() {
        var query = entityManager.createNativeQuery("""
                WITH LatestSnapshots AS (
                    SELECT
                        s.id,
                        s.torrent,
                        s.peer_ip,
                        s.user_application,
                        s.to_peer_traffic,
                        ROW_NUMBER() OVER (PARTITION BY s.torrent, s.peer_ip, s.user_application ORDER BY s.last_time_seen DESC) AS rn
                    FROM
                        public.peer_history s
                    WHERE
                        s.last_time_seen >= ?
                ),
                AggregatedUploads AS (
                    SELECT
                        ls.torrent,
                        ls.peer_ip,
                        SUM(ls.to_peer_traffic) AS total_uploaded
                    FROM
                        LatestSnapshots ls
                    WHERE
                        ls.rn = 1
                    GROUP BY
                        ls.torrent,
                        ls.peer_ip
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
                    au.total_uploaded > t.size * ? AND t.size::float > 0
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
    }

//
//    @Transactional
//    @Modifying
//    @Lock(LockModeType.READ)
//    @Scheduled(fixedDelayString = "${analyse.untrustip.interval}")
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
                    if (ip.isIPv6()) {
                        ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength).toZeroHost();
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
