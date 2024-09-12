package com.ghostchu.btn.sparkle.module.analyse;

import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRuleRepository;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.util.IPMerger;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.MsgUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyseService {
    private static final String UNTRUSTED_IP = "不受信任 IP 地址";
    private static final String OVER_DOWNLOAD = "BTN 网络 IP 下载量分析";
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
    @Autowired
    private AnalysedRuleRepository analysedRuleRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    @Modifying
    @Lock(LockModeType.READ)
    @Scheduled(fixedDelayString = "${analyse.overdownload.interval}")
    public void cronUntrustedIPAddresses() {
        var list = ipMerger.merge(banHistoryRepository
                .generateUntrustedIPAddresses(new Timestamp(System.currentTimeMillis() - untrustedIpAddressGenerateOffset), new Timestamp(System.currentTimeMillis()), untrustedIpAddressGenerateThreshold)
                .stream()
                .map(IPUtil::toString)
                .collect(Collectors.toList()));
        var untrustedIps = list.stream().map(ip -> new AnalysedRule(null, ip, UNTRUSTED_IP, "Generated at " + MsgUtil.getNowDateTimeString())).toList();
        analysedRuleRepository.deleteAllByModule(UNTRUSTED_IP);
        analysedRuleRepository.saveAll(untrustedIps);
    }

    @Transactional
    public List<AnalysedRule> getAnalysedRules(){
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
    @Scheduled(fixedDelayString = "${analyse.untrustip.interval}")
    public void cronUpdateUnTrustIps() {
        var query = entityManager.createNativeQuery("""
                WITH LatestSnapshots AS (
                    SELECT
                        s.id,
                        s.torrent,
                        s.peer_ip,
                        s.user_application,
                        s.to_peer_traffic,
                        ROW_NUMBER() OVER (PARTITION BY s.torrent, s.peer_ip, s.user_application ORDER BY s.insert_time DESC) AS rn
                    FROM
                        snapshot s
                    WHERE
                        s.insert_time >= ? AND s.to_peer_traffic > 0
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
                    (au.total_uploaded / t.size::float) AS upload_percentage
                FROM
                    AggregatedUploads au
                JOIN
                    torrent t ON au.torrent = t.id
                WHERE
                    au.total_uploaded > t.size * ?
                ORDER BY
                    au.total_uploaded DESC;
                """);
        query.setParameter(1, new Timestamp(System.currentTimeMillis() - overDownloadGenerateOffset));
        query.setParameter(2, overDownloadGenerateThreshold);
        List<Object[]> queryResult = query.getResultList();
        var ips = ipMerger.merge(queryResult.stream().map(arr -> IPUtil.toString(((InetAddress) arr[1]))).collect(Collectors.toList()));
        List<AnalysedRule> rules = new ArrayList<>();
        for (String ip : ips) {
            rules.add(new AnalysedRule(
                    null,
                    ip,
                    OVER_DOWNLOAD,
                    "Generated at " + MsgUtil.getNowDateTimeString()
            ));
        }
        analysedRuleRepository.deleteAllByModule(OVER_DOWNLOAD);
        analysedRuleRepository.saveAll(rules);
    }
}
