package com.ghostchu.btn.sparkle.module.analyse;

import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRuleRepository;
import com.ghostchu.btn.sparkle.module.analyse.proto.Peer;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientDiscoveryService;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientIdentity;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.util.*;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
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
    @Value("${analyse.tracker.dumpfile}")
    private String dumpFilePath;
    @Autowired
    private AnalysedRuleRepository analysedRuleRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private UserService userService;
    @Autowired
    private ClientDiscoveryService clientDiscoveryService;
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
            var ipTries = new DualIPv4v6Tries();
            ipMerger.merge(banHistoryRepository
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
                    .forEach(ipTries::add);
            List<AnalysedRule> untrustedIps = new ArrayList<>();
            filterIP(ipTries).forEach(ip -> untrustedIps.add(new AnalysedRule(null, ip.toString(), UNTRUSTED_IP, "Generated at " + MsgUtil.getNowDateTimeString())));
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
            final var ipTries = new DualIPv4v6Tries();
            banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
                if (query != null)
                    query.distinct(true);
                return criteriaBuilder.and(criteriaBuilder.between(root.get("insertTime"), pastTimestamp(highRiskIpsOffset), nowTimestamp()),
                        criteriaBuilder.equal(root.get("module"), PCB_MODULE_NAME),
                        criteriaBuilder.like(root.get("peerClientName"), "aria2/%"));
            }, page -> page.forEach(rule -> {
                try {
                    ipTries.add(IPUtil.toIPAddress(rule.getPeerIp().getHostAddress()));
                } catch (Exception e) {
                    log.error("Unable to convert IP address: {}", rule.getPeerIp().getHostAddress(), e);
                }
            }));
            var filtered = filterIP(ipTries);
            var highRiskIps = ipMerger.merge(filtered)
                    .stream()
                    .map(ip -> new AnalysedRule(null, ip, HIGH_RISK_IP, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
            analysedRuleRepository.replaceAll(HIGH_RISK_IP, highRiskIps);
            meterRegistry.gauge("sparkle_analyse_high_risk_ips", Collections.emptyList(), highRiskIps.size());
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
            final var ipTries = new DualIPv4v6Tries();
            banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
                if (query != null)
                    query.distinct(true);
                return criteriaBuilder.and(criteriaBuilder.between(root.get("insertTime"), pastTimestamp(highRiskIpv6IdentityOffset), nowTimestamp()),
                        criteriaBuilder.equal(root.get("module"), PCB_MODULE_NAME),
                        criteriaBuilder.or(
                                criteriaBuilder.like(root.get("peerIp").as(String.class), "%::1"),
                                criteriaBuilder.like(root.get("peerIp").as(String.class), "%::2")
                        ));
            }, page -> page.forEach(rule -> {
                try {
                    ipTries.add(IPUtil.toIPAddress(rule.getPeerIp().getHostAddress()));
                } catch (Exception e) {
                    log.error("Unable to convert IP address: {}", rule.getPeerIp().getHostAddress(), e);
                }
            }));
            var filtered = filterIP(ipTries);
            var ips = ipMerger.merge(filtered).stream().map(ip -> new AnalysedRule(null, ip, HIGH_RISK_IPV6_IDENTITY, "Generated at " + MsgUtil.getNowDateTimeString()))
                    .toList();
            analysedRuleRepository.replaceAll(HIGH_RISK_IPV6_IDENTITY, ips);
            meterRegistry.gauge("sparkle_analyse_high_risk_ipv6_identity", Collections.emptyList(), ips.size());
            log.info("High risk IPV6 identity: {}, tooked {} ms", ips.size(), System.currentTimeMillis() - startAt);
        } finally {
            DatabaseCare.generateParallel.release();
        }
    }

    public List<AnalysedRule> getHighRiskIPV6Identity() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(HIGH_RISK_IPV6_IDENTITY);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedRateString = "${analyse.trackerhighrisk.interval}")
    public void cronUpdateTrunkerFile() {
        // Trunker, A BitTorrent Tracker, not a typo but a name
        var startAt = System.currentTimeMillis();
        final var ipTries = new DualIPv4v6Tries();
        Set<ClientIdentity> clientDiscoveries = new HashSet<>();
        var user = userService.getSystemUser("Tracker");

        scanFile(peerInfo -> {
            var peerId = new String(peerInfo.getPeerId().toByteArray(), StandardCharsets.ISO_8859_1);
            try {
                clientDiscoveries.add(new ClientIdentity(PeerUtil.cutPeerId(peerId), PeerUtil.cutClientName("[UA] " + peerInfo.getUserAgent())));
                if (peerInfo.getUserAgent().contains("curl/")
                        || (peerInfo.getUserAgent().contains("Transmission") && !peerId.startsWith("-TR"))
                ) {
                    ipTries.add(IPUtil.toIPAddress(peerInfo.getIp().getClientIp().toByteArray()));
                }
            } catch (Exception e) {
                log.error("Unable to handle PeerInfo check: {}", peerInfo, e);
            } finally {
                if (clientDiscoveries.size() > 500) {
                    clientDiscoveryService.handleIdentities(user, OffsetDateTime.now(), OffsetDateTime.now(), clientDiscoveries);
                    clientDiscoveries.clear();
                }
            }
        });

        var filtered = filterIP(ipTries);
        var ips = ipMerger.merge(filtered).stream().map(ip -> new AnalysedRule(null, ip, TRACKER_HIGH_RISK, "Generated at " + MsgUtil.getNowDateTimeString()))
                .toList();
        analysedRuleRepository.replaceAll(TRACKER_HIGH_RISK, ips);
        meterRegistry.gauge("sparkle_analyse_tracker_high_risk_identity", Collections.emptyList(), ips.size());
        log.info("Tracker HighRisk identity: {}, tooked {} ms", ips.size(), System.currentTimeMillis() - startAt);
    }

    private void scanFile(Consumer<Peer.PeerInfo> predicate) {
        File file = new File(dumpFilePath);
        if (!file.exists()) {
            log.error("Tracker dump file not found: {}", dumpFilePath);
            return;
        }
        try (FileInputStream fis = new FileInputStream(file);
             FileLock ignored = fis.getChannel().lock(0L, Long.MAX_VALUE, true)) {
            LargeFileReader reader = new LargeFileReader(fis.getChannel());
            while (reader.available() > 0) {
                int sizeInIntNeedToFix = ByteBuffer.wrap(reader.read(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
                long remainingToRead = Integer.toUnsignedLong(sizeInIntNeedToFix);
                byte[] buffer = reader.read((int) remainingToRead);
                predicate.accept(Peer.PeerInfo.parseFrom(buffer));
            }
        } catch (IOException e) {
            log.error("Unable to read tracker dump file: {}", dumpFilePath, e);
        }
    }

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
            var ipTries = new DualIPv4v6Tries();
            for (Object[] arr : queryResult) {
                var ipAddr = IPUtil.toIPAddress(((InetAddress) arr[1]).getHostAddress());
                ipTries.add(ipAddr);
            }
            ipTries = filterIP(ipTries);
            List<AnalysedRule> rules = new ArrayList<>();
            ipMerger.merge(ipTries).forEach(i -> rules.add(new AnalysedRule(null, i, OVER_DOWNLOAD,
                    "Generated at " + MsgUtil.getNowDateTimeString())));
            analysedRuleRepository.replaceAll(OVER_DOWNLOAD, rules);
            meterRegistry.gauge("sparkle_analyse_over_download_ips", Collections.emptyList(), rules.size());
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

    public DualIPv4v6Tries filterIP(DualIPv4v6Tries ips) {
        DualIPv4v6Tries dualIPv4v6Tries = new DualIPv4v6Tries();
        ips.forEach(ip -> {
            if (!ip.isLocal() && !ip.isLoopback()) {
                try {
                    if (ip.getPrefixLength() == null && ip.isIPv6()) {
                        ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength).toZeroHost();
                    }
                    dualIPv4v6Tries.add(ip);
                } catch (Exception e) {
                    log.error("Unable to convert {} with prefix block {}.", ip, ipv6ConvertToPrefixLength, e);
                }
            }
        });
        return dualIPv4v6Tries;
    }


    private OffsetDateTime nowTimestamp() {
        return OffsetDateTime.now();
    }

    private OffsetDateTime pastTimestamp(long offset) {
        return OffsetDateTime.now().minus(offset, ChronoUnit.MILLIS);
    }
}
