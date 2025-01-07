package com.ghostchu.btn.sparkle.module.analyse;

import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRuleRepository;
import com.ghostchu.btn.sparkle.module.analyse.proto.Peer;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientDiscoveryService;
import com.ghostchu.btn.sparkle.module.clientdiscovery.ClientIdentity;
import com.ghostchu.btn.sparkle.util.*;
import com.google.common.hash.BloomFilter;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final String TEMPLATE_TRACKER_DATA = "Tracker 数据 {PeerId: {}, 端口号: {}, 汇报事件: {}, " +
            "剩余需要下载的字节数（自汇报）: {}, 上传字节数（自汇报）: {}, 下载字节数（自汇报）: {}, UserAgent: {}}";

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
    private ClientDiscoveryService clientDiscoveryService;
//    @Autowired
//    private RedisTrackedPeerRepository redisTrackedPeerRepository;

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(cron = "${analyse.untrustip.interval}")
    public void cronUntrustedIPAddresses() {
        var startAt = System.currentTimeMillis();
        DualIPv4v6AssociativeTries<Integer> ipTries = new DualIPv4v6AssociativeTries<>();
            /*
            CREATE MATERIALIZED VIEW progress_cheat_blocker_agg_view
WITH (timescaledb.continuous) AS
SELECT
    time_bucket(INTERVAL '1 hour', insert_time) AS bucket,
    peer_ip,
    COUNT(DISTINCT user_application) AS app_count
FROM banhistory
WHERE module = 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker'
GROUP BY bucket, peer_ip
WITH NO DATA;


SELECT add_continuous_aggregate_policy('progress_cheat_blocker_agg_view',
start_offset => INTERVAL '7 day',
end_offset => INTERVAL '1 minute',
schedule_interval => INTERVAL '1 hour');


CREATE MATERIALIZED VIEW untrustip_agg
WITH (timescaledb.continuous) AS
SELECT time_bucket('7 day', "insert_time") AS bucket, peer_ip, COUNT(DISTINCT user_application) FROM banhistory
                WHERE
                    "module" LIKE '%ProgressCheatBlocker%'

                GROUP BY bucket, peer_ip ORDER BY count DESC
             */
        var ips = banHistoryRepository
                .generateUntrustedIPAddresses(
                        TimeUtil.toUTC(System.currentTimeMillis() - untrustedIpAddressGenerateOffset),
                        OffsetDateTime.now(),
                        untrustedIpAddressGenerateThreshold
                );
        ips.forEach(projection -> {
            try {
                ipTries.putNode(IPUtil.toIPAddress(projection.getPeerIp().getHostAddress()), projection.getCount());
            } catch (Exception e) {
                log.error("Unable to convert IP address: {}", projection.getPeerIp().getHostAddress(), e);
            }
        });
        var filtered = filterIP(ipTries);
        List<AnalysedRule> rules = new ArrayList<>();
        filtered.nodeIterator(false).forEachRemaining(node -> rules.add(new AnalysedRule(null, node.getKey().toNormalizedString(), UNTRUSTED_IP,
                "[AutoGen] 被 " + node.getValue() + " 个用户应用程序上报为不信任的 IP 地址")));
        meterRegistry.gauge("sparkle_analyse_untrusted_ip_address", Collections.emptyList(), rules.size());
        analysedRuleRepository.replaceAll(UNTRUSTED_IP, rules);
        log.info("Untrusted IPs: {}, tooked {} ms", rules.size(), System.currentTimeMillis() - startAt);
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
    @Scheduled(cron = "${analyse.refreshanalyse.interval}")
    public void cronRefreshAnalyse() {
        var startAt = System.currentTimeMillis();
        var refreshSnapshots = entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW analyse_most_banned_peers_ip;");
        refreshSnapshots.executeUpdate();
        var refreshAggregated = entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW analyse_most_banned_peers_ip_pcb;");
        refreshAggregated.executeUpdate();
        log.info("Refreshed materialized views for general analyse, tooked {} ms", System.currentTimeMillis() - startAt);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(cron = "${analyse.highriskips.interval}")
    public void cronHighRiskIps() {
        var startAt = System.currentTimeMillis();
        final var ipTries = new DualIPv4v6Tries();
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null) query.distinct(true);
            return criteriaBuilder.and(criteriaBuilder.between(root.get("insertTime"), pastTimestamp(highRiskIpsOffset), nowTimestamp()), criteriaBuilder.equal(root.get("module"), PCB_MODULE_NAME), criteriaBuilder.like(root.get("peerClientName"), "aria2/%"));
        }, page -> page.forEach(rule -> {
            try {
                ipTries.add(IPUtil.toIPAddress(rule.getPeerIp().getHostAddress()));
            } catch (Exception e) {
                log.warn("Unable to convert IP address: {}", rule.getPeerIp().getHostAddress(), e);
            }
        }));
        var filtered = filterIP(ipTries);
        var highRiskIps = ipMerger.merge(filtered).stream().distinct().map(ip -> new AnalysedRule(null, ip, HIGH_RISK_IP, "Auto Generated by Sparkle")).collect(Collectors.toSet());
        analysedRuleRepository.replaceAll(HIGH_RISK_IP, highRiskIps);
        meterRegistry.gauge("sparkle_analyse_high_risk_ips", Collections.emptyList(), highRiskIps.size());
        log.info("High risk IPs: {}, tooked {} ms", highRiskIps.size(), System.currentTimeMillis() - startAt);
    }

    public List<AnalysedRule> getHighRiskIps() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(HIGH_RISK_IP);
    }
//
//    @Transactional
//    @Modifying
//    @Lock(LockModeType.READ)
//    @Scheduled(fixedRateString = "${analyse.highriskipv6identity.interval}")
//    public void cronHighRiskIPV6Identity() throws InterruptedException {
//        try {
//            DatabaseCare.generateParallel.acquire();
//            var startAt = System.currentTimeMillis();
//            final var ipTries = new DualIPv4v6Tries();
//            banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
//                if (query != null)
//                    query.distinct(true);
//
//                return criteriaBuilder.and(criteriaBuilder.between(root.get("insertTime"), pastTimestamp(highRiskIpv6IdentityOffset), nowTimestamp()),
//                        criteriaBuilder.equal(root.get("module"), PCB_MODULE_NAME),
//                        criteriaBuilder.or(
//                                criteriaBuilder.like(criteriaBuilder.function("CAST", String.class, root.get("peerIp"), criteriaBuilder.like("text")), "%::1"),
//                                criteriaBuilder.like(criteriaBuilder.function("CAST", String.class, root.get("peerIp"), criteriaBuilder.literal("text")), "%::2")
//                        ));
//            }, page -> page.forEach(rule -> {
//                try {
//                    ipTries.add(IPUtil.toIPAddress(rule.getPeerIp().getHostAddress()));
//                } catch (Exception e) {
//                    log.error("Unable to convert IP address: {}", rule.getPeerIp().getHostAddress(), e);
//                }
//            }));
//            var filtered = filterIP(ipTries);
//            var ips = ipMerger.merge(filtered).stream().map(ip -> new AnalysedRule(null, ip, HIGH_RISK_IPV6_IDENTITY, "Generated at " + MsgUtil.getNowDateTimeString()))
//                    .toList();
//            analysedRuleRepository.replaceAll(HIGH_RISK_IPV6_IDENTITY, ips);
//            meterRegistry.gauge("sparkle_analyse_high_risk_ipv6_identity", Collections.emptyList(), ips.size());
//            log.info("High risk IPV6 identity: {}, tooked {} ms", ips.size(), System.currentTimeMillis() - startAt);
//        } finally {
//            DatabaseCare.generateParallel.release();
//        }
//    }

    // CREATE MATERIALIZED VIEW analyse_recent_country_region_mdb_ban_trends
    //WITH (timescaledb.continuous) AS
    //SELECT
    //  time_bucket('1 day', "insert_time") AS day,
    //  geoip ->> 'countryIso' AS iso,
    //  COUNT ( 1 ) AS ct,
    //  COUNT ( DISTINCT peer_ip ) AS ct_ip
    //FROM
    //  banhistory
    //WHERE
    //  "module" = 'com.ghostchu.peerbanhelper.module.impl.rule.MultiDialingBlocker'
    //GROUP BY
    //  day, iso
    //ORDER BY ct DESC
    //WITH NO DATA


    //CREATE MATERIALIZED VIEW analyse_most_banned_peers_ip_pcb AS
    //SELECT
    //  CASE
    //    -- 对于 IPv4 地址，先设置 /24 前缀，再提取网络部分
    //    WHEN family(peer_ip::inet) = 4 THEN network(set_masklen(peer_ip::cidr, 24))
    //    -- 对于 IPv6 地址，先设置 /56 前缀，再提取网络部分
    //    WHEN family(peer_ip::inet) = 6 THEN network(set_masklen(peer_ip::cidr, 56))
    //  END AS peer_ip_cidr,
    //  geoip ->> 'countryIso' AS iso,
    //  geoip ->> 'cityName' AS city,
    //  COUNT(peer_ip) AS ct
    //FROM
    //  banhistory
    //WHERE
    //  insert_time > NOW() - INTERVAL '7 days'
    //  AND
    //  module = 'com.ghostchu.peerbanhelper.module.impl.rule.ProgressCheatBlocker'
    //GROUP BY
    //  peer_ip_cidr, geoip ->> 'countryIso', geoip ->> 'cityName'
    //ORDER BY
    //  ct DESC
    //LIMIT 100
    //WITH DATA;

    public List<AnalysedRule> getHighRiskIPV6Identity() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(HIGH_RISK_IPV6_IDENTITY);
    }

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(cron = "${analyse.trackerhighrisk.interval}")
    public void cronUpdateTrunkerFile() {
        // Trunker, A BitTorrent Tracker, not a typo but a name
        var startAt = System.currentTimeMillis();
        List<ClientIdentity> clientDiscoveries = new CopyOnWriteArrayList<>();
        BloomFilter<String> bloomFilter = BloomFilter.create((from, into) -> into.putString(from, StandardCharsets.ISO_8859_1), 10_000_000, 0.01);
        AtomicLong count = new AtomicLong(0);
        AtomicLong success = new AtomicLong(0);
        DualIPv4v6AssociativeTries<Peer.PeerInfo> ipTries = readDataFromTrackerPersistFile(count, bloomFilter, clientDiscoveries, success);
        List<AnalysedRule> rules = new ArrayList<>();
        ipTries = filterIP(ipTries);
        var it = ipTries.nodeIterator(false);
        while (it.hasNext()) {
            var node = it.next();
            //                   StringJoiner joiner = new StringJoiner("\n");
            String info = null;
            if (node.getValue() != null) {
                var filteredUserAgent = ByteUtil.filterUTF8(node.getValue().getUserAgent().chars().filter(c -> c >= 32).mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining()));
                var filteredPeerId = ByteUtil.filterUTF8(node.getValue().getPeerId().toString(StandardCharsets.ISO_8859_1).chars().filter(c -> c >= 32).mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining()));
                // 查询此 Peer 最近一次
                // 这里未来还可以查询 BTN 库
                info = MsgUtil.fillArgs(TEMPLATE_TRACKER_DATA, filteredPeerId,
                        String.valueOf(node.getValue().getPort()), node.getValue().getEvent().name(), String.valueOf(node.getValue().getLeft()), String.valueOf(node.getValue().getUploaded()), String.valueOf(node.getValue().getDownloaded()), filteredUserAgent);
            }
            rules.add(new AnalysedRule(null, node.getKey().toNormalizedString(), TRACKER_HIGH_RISK, ByteUtil.filterUTF8("[AutoGen] " + info)));
        }
        analysedRuleRepository.replaceAll(TRACKER_HIGH_RISK, rules);
        meterRegistry.gauge("sparkle_analyse_tracker_high_risk_identity", Collections.emptyList(), rules.size());
        log.info("Tracker HighRisk identity: {}, tooked {} ms; success: {}/{}.", rules.size(), System.currentTimeMillis() - startAt, success.get(), count.get());
    }

    private DualIPv4v6AssociativeTries<Peer.PeerInfo> readDataFromTrackerPersistFile(AtomicLong count, BloomFilter<String> bloomFilter, List<ClientIdentity> clientDiscoveries, AtomicLong success) {
        var ipTries = new DualIPv4v6AssociativeTries<Peer.PeerInfo>();
        try (var service = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()))) {
            scanFile(peerInfo -> {
                count.incrementAndGet();
                var peerId = new String(peerInfo.getPeerId().toByteArray(), StandardCharsets.ISO_8859_1);
                var peerClientName = peerInfo.getUserAgent();
                if (peerClientName.length() > 250) {
                    // get the first 64 chars and append "..." and append last 64 chars
                    peerClientName = peerClientName.substring(0, 64) + "[...]" + peerClientName.substring(peerClientName.length() - 64);
                }
                try {
                    if (bloomFilter.put(peerId + "@@@" + peerClientName)) {
                        synchronized (clientDiscoveries) {
                            clientDiscoveries.add(new ClientIdentity(PeerUtil.cutPeerId(peerId), PeerUtil.cutClientName("[UA] " + peerInfo.getUserAgent())));
                        }
                    }
                    if (
                            (peerInfo.getUserAgent().contains("Transmission") != peerId.startsWith("-TR"))
                                    || (peerInfo.getUserAgent().contains("aria2") != peerId.startsWith("A2"))
                    ) {
                        var ip = IPUtil.toIPAddress(peerInfo.getIp().getClientIp().toByteArray());
                        if (!ip.isLocal() && !ip.isLoopback()) {
                            try {
                                if (ip.getPrefixLength() == null && ip.isIPv6()) {
                                    ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength);
                                }
                                // 去除控制符，保留其他所有字符
                                ipTries.putNode(ip, peerInfo);
                            } catch (Exception e) {
                                log.error("Unable to convert {} with prefix block {}.", ip, ipv6ConvertToPrefixLength, e);
                            }
                        }

                    }
                    success.incrementAndGet();
                } catch (Exception e) {
                    log.debug("Unable to handle PeerInfo check: {}, clientIp is {}", peerInfo, Arrays.toString(peerInfo.getIp().getClientIp().toByteArray()), e);
                } finally {
                    if (clientDiscoveries.size() > 10000) {
                        synchronized (clientDiscoveries) {
                            clientDiscoveryService.handleIdentities(OffsetDateTime.now(), OffsetDateTime.now(), clientDiscoveries);
                            clientDiscoveries.clear();
                        }
                    }

                }
            }, service);
        }
        return ipTries;
    }

    private void scanFile(Consumer<Peer.PeerInfo> predicate, ExecutorService service) {
        File file = new File(dumpFilePath);
        if (!file.exists()) {
            log.error("Tracker dump file not found: {}", dumpFilePath);
            return;
        }
        try (FileInputStream fis = new FileInputStream(file); FileLock ignored = fis.getChannel().lock(0L, Long.MAX_VALUE, true)) {
            while (fis.available() > 0) {
                int sizeInIntNeedToFix = ByteBuffer.wrap(fis.readNBytes(4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
                long remainingToRead = Integer.toUnsignedLong(sizeInIntNeedToFix);
                byte[] buffer = fis.readNBytes((int) remainingToRead);
                var peerInfo = Peer.PeerInfo.parseFrom(buffer);
                service.submit(() -> predicate.accept(peerInfo));
            }
        } catch (IOException e) {
            log.error("Unable to read tracker dump file: {}", dumpFilePath, e);
        }
    }

    public List<AnalysedRule> getTrackerHighRisk() {
        return analysedRuleRepository.findByModuleOrderByIpAsc(TRACKER_HIGH_RISK);
    }

    @Scheduled(cron = "${analyse.overdownload.refreshviews.interval}")
    @Transactional
    @Modifying
    public void cronRefreshMaterializedViews() {
        var startAt = System.currentTimeMillis();
        var refreshSnapshots = entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW overdownload_latest_snapshots;");
        refreshSnapshots.executeUpdate();
        var refreshAggregated = entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW overdownload_aggregated_uploads;");
        refreshAggregated.executeUpdate();
        log.info("Refreshed materialized views for overdownload analyse, tooked {} ms", System.currentTimeMillis() - startAt);
    }

    /*
    CREATE MATERIALIZED VIEW overdownload_latest_snapshots AS
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
                        s.last_time_seen >= NOW() - INTERVAL '7 days' AND s.to_peer_traffic > 0
                    ORDER BY
                        s.torrent, s.peer_ip, s.user_application, s.last_time_seen DESC

WITH NO DATA


CREATE MATERIALIZED VIEW overdownload_aggregated_uploads AS
SELECT
                        ls.torrent,
                        ls.peer_ip,
                        SUM(ls.to_peer_traffic) AS total_uploaded,
						 t.size AS torrent_size
                    FROM
                        overdownload_latest_snapshots ls
					LEFT JOIN
                    	torrent t ON ls.torrent = t.id
                    GROUP BY
                        ls.torrent, ls.peer_ip, t.size
                    HAVING
                        SUM(ls.to_peer_traffic) > t.size
WITH NO DATA

     */

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(cron = "${analyse.overdownload.interval}")
    public void cronUpdateOverDownload() {
        var startAt = System.currentTimeMillis();
        var query = entityManager.createNativeQuery("""
                SELECT
                    au.torrent,
                    au.peer_ip,
                    au.total_uploaded,
                    au.torrent_size,
                    (au.total_uploaded / au.torrent_size::float) * 100 AS upload_percentage
                FROM
                    overdownload_aggregated_uploads au
                WHERE
                    au.torrent_size::float > 0 AND au.total_uploaded > au.torrent_size::float * ?
                ORDER BY
                    upload_percentage DESC;
                """);
//        query.setParameter(1, new Timestamp(System.currentTimeMillis() - overDownloadGenerateOffset));
        query.setParameter(1, overDownloadGenerateThreshold);
        List<Object[]> queryResult = query.getResultList();
        DualIPv4v6AssociativeTries<String> ipTries = new DualIPv4v6AssociativeTries<>();
        for (Object[] arr : queryResult) {
            if (Long.parseLong(arr[3].toString()) < 1024 * 1024 * 64 && Long.parseLong(arr[2].toString()) < 1024 * 1024 * 128) {
                continue;
            }
            var ipAddr = IPUtil.toIPAddress(((InetAddress) arr[1]).getHostAddress());
            ipTries.putNode(ipAddr, "从大小为 " + FileUtils.byteCountToDisplaySize(Long.parseLong(arr[3].toString())) + " 的种子上下载了 " + FileUtils.byteCountToDisplaySize(Long.parseLong(arr[2].toString())) + " 的数据。下载比(100%=完整下载1次种子的大小)： " + String.format("%.2f", Double.parseDouble(arr[4].toString())) + "%");
        }
        var filtered = filterIP(ipTries);
        List<AnalysedRule> rules = new ArrayList<>();
        filtered.nodeIterator(false).forEachRemaining(node -> rules.add(new AnalysedRule(null, node.getKey().toString(), OVER_DOWNLOAD, "[AutoGen] " + node.getValue())));
        analysedRuleRepository.replaceAll(OVER_DOWNLOAD, rules);
        meterRegistry.gauge("sparkle_analyse_over_download_ips", Collections.emptyList(), rules.size());
        log.info("Over download IPs: {}, tooked {} ms", rules.size(), System.currentTimeMillis() - startAt);
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
        return list.stream().filter(ip -> !ip.isLocal() && !ip.isLoopback()).map(ip -> {
            try {
                if (ip.getPrefixLength() == null && ip.isIPv6()) {
                    ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength);
                }
            } catch (Exception e) {
                log.error("Unable to convert {} with prefix block {}.", ip, ipv6ConvertToPrefixLength, e);
            }
            return ip;
        }).distinct().collect(Collectors.toList());
    }

    public DualIPv4v6Tries filterIP(DualIPv4v6Tries ips) {
        DualIPv4v6Tries dualIPv4v6Tries = new DualIPv4v6Tries();
        ips.forEach(ip -> {
            if (!ip.isLocal() && !ip.isLoopback()) {
                try {
                    if (ip.getPrefixLength() == null && ip.isIPv6()) {
                        ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength);
                    }
                    dualIPv4v6Tries.add(ip);
                } catch (Exception e) {
                    log.error("Unable to convert {} with prefix block {}.", ip, ipv6ConvertToPrefixLength, e);
                }
            }
        });
        return dualIPv4v6Tries;
    }

    public <T> DualIPv4v6AssociativeTries<T> filterIP(DualIPv4v6AssociativeTries<T> ips) {
        DualIPv4v6AssociativeTries<T> dualIPv4v6Tries = new DualIPv4v6AssociativeTries<>();
        ips.nodeIterator(false).forEachRemaining(node -> {
            var ip = node.getKey();
            if (!ip.isLocal() && !ip.isLoopback()) {
                try {
                    if (ip.getPrefixLength() == null && ip.isIPv6()) {
                        ip = ip.toPrefixBlock(ipv6ConvertToPrefixLength);
                    }
                    dualIPv4v6Tries.put(ip, node.getValue());
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

    public record AnalysedAddressIsoCityCt(
            String ip,
            String iso,
            String city,
            long ct
    ) {

    }
}
