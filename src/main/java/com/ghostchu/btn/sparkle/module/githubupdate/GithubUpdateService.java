package com.ghostchu.btn.sparkle.module.githubupdate;

import com.ghostchu.btn.sparkle.module.analyse.AnalyseService;
import com.ghostchu.btn.sparkle.module.analyse.impl.AnalysedRule;
import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.util.IPUtil;
import jakarta.transaction.Transactional;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.function.Supplier;

@Service
@Slf4j
public class GithubUpdateService {
    private final BanHistoryRepository banHistoryRepository;
    private final AnalyseService analyseService;
    private final BanHistoryService banHistoryService;
    @Value("${service.githubruleupdate.access-token}")
    private String accessToken;
    @Value("${service.githubruleupdate.org-name}")
    private String orgName;
    @Value("${service.githubruleupdate.repo-name}")
    private String repoName;
    @Value("${service.githubruleupdate.branch-name}")
    private String branchName;
    @Value("${service.githubruleupdate.past-interval}")
    private long pastInterval;

    public GithubUpdateService(BanHistoryRepository banHistoryRepository, AnalyseService analyseService, BanHistoryService banHistoryService) {
        this.banHistoryRepository = banHistoryRepository;
        this.analyseService = analyseService;
        this.banHistoryService = banHistoryService;
    }


    @Scheduled(cron = "${service.githubruleupdate.interval}")
    @Transactional
    public void githubRuleUpdate() throws IOException, InterruptedException {
        log.info("开始更新 GitHub 同步规则存储库...");
        GitHub github = new GitHubBuilder().withOAuthToken(accessToken, orgName).build();
        var organization = github.getOrganization(orgName);
        if (organization == null) {
            throw new IllegalArgumentException("Organization " + orgName + " not found");
        }
        var repository = organization.getRepository(repoName);
        updateFile(repository, "untrusted-ips.txt", () -> generateUntrustedIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "high-risk-ips.txt", () -> generateHighRiskIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "overdownload-ips.txt", () -> generateOverDownloadIps().getBytes(StandardCharsets.UTF_8));
        //updateFile(repository, "strange_ipv6_block.txt", () -> generateStrangeIPV6().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "random-peerid.txt", () -> generateGopeedDev().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "hp_torrent.txt", () -> generateHpTorrents().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "dt_torrent.txt", () -> generateDtTorrents().getBytes(StandardCharsets.UTF_8));
        //updateFile(repository, "go.torrent dev 20181121.txt", () -> generateBaiduNetdisk().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "0xde-0xad-0xbe-0xef.txt", () -> generateDeadBeef().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "123pan.txt", () -> generate123pan().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "tracker-high-risk-ips.txt", () -> generateTrackerHighRiskIps().getBytes(StandardCharsets.UTF_8));
    }

    private String generateTrackerHighRiskIps() {
        var strJoiner = new StringJoiner("\n");
        analyseService.getTrackerHighRisk()
                .stream()
                .map(AnalysedRule::getIp)
                .distinct()
                .forEach(strJoiner::add);;
        return strJoiner.toString();
    }

    private String generateUntrustedIps() {
        var strJoiner = new StringJoiner("\n");
        analyseService.getUntrustedIPAddresses()
                .stream()
                .map(AnalysedRule::getIp)
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

//    private String generateDhcpAddress() {
//        var strJoiner = new StringJoiner("\n");
//        Set<String> ipSet = new HashSet<>();
//        var result = banHistoryRepository.findByInsertTimeBetweenOrderByInsertTimeDescIPVx(pastTimestamp(), nowTimestamp(), 6);
//        result
//                .stream()
//                .map(ban -> new IPAddressString(ban.getHostAddress()).getAddress())
//                .filter(Objects::nonNull)
//                .filter(ip -> ip.isIPv6() && ip.toFullString().contains(":0000:0000:0000:"))
//                .forEach(ip -> ipSet.add(ip.toString()));
//        ipSet.stream().sorted().forEach(strJoiner::add);
//        return strJoiner.toString();
//    }

    private String generateHighRiskIps() {
        var strJoiner = new StringJoiner("\n");
        analyseService.getHighRiskIps()
                .stream()
                .map(AnalysedRule::getIp)
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

//    private String generateStrangeIPV6() {
//        var strJoiner = new StringJoiner("\n");
//        analyseService.getHighRiskIPV6Identity().forEach(r -> strJoiner.add(r.getIp()));
//        return strJoiner.toString();
//    }

    private String generateGopeedDev() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null)
                query.distinct(true);
            return criteriaBuilder.or(
                    criteriaBuilder.like(root.get("peerClientName"), "Gopeed dev%"),
                    criteriaBuilder.like(root.get("peerId"), "-gp%")
            );
        }, (page) -> page
                .stream()
                .map(ban -> ban.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add));
        return strJoiner.toString();
    }

    private String generate123pan() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null)
                query.distinct(true);
            return criteriaBuilder.like(root.get("peerClientName"), "offline-download (devel) (anacrolix/torrent unknown)%");
        }, (page) -> page
                .stream()
                .map(ban -> ban.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add));
        return strJoiner.toString();
    }

    private String generateDeadBeef() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null)
                query.distinct(true);
            return criteriaBuilder.like(root.get("peerClientName"), "ޭ__%");
        }, (page) -> page
                .stream()
                .map(ban -> ban.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add));
        return strJoiner.toString();
    }

    private String generateBaiduNetdisk() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByInsertTimeBetweenAndPeerClientNameLike(
                        pastTimestamp(),
                        nowTimestamp(),
                        "go.torrent dev 20181121%"
                )
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }


    private String generateDtTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null)
                query.distinct(true);
            return criteriaBuilder.or(
                    criteriaBuilder.like(root.get("peerClientName"), "dt/torrent%"),
                    criteriaBuilder.like(root.get("peerId"), "-DT%")
            );
        }, (page) -> page
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add));
        return strJoiner.toString();
    }

    private String generateHpTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findAllByPaging((Specification<BanHistory>) (root, query, criteriaBuilder) -> {
            if (query != null)
                query.distinct(true);
            return criteriaBuilder.or(
                    criteriaBuilder.like(root.get("peerClientName"), "hp/torrent%"),
                    criteriaBuilder.like(root.get("peerId"), "-HP%")
            );
        }, (page) -> page
                .stream()
                .map(history -> IPUtil.toString(history.getPeerIp()))
                .sorted()
                .distinct()
                .forEach(strJoiner::add));
        return strJoiner.toString();
    }

    private void updateFile(GHRepository repository, String file, Supplier<byte[]> contentSupplier) {
        try {
            var oldFile = repository.getFileContent(file);
            var sha = oldFile != null ? oldFile.getSha() : null;
            var content = contentSupplier.get();
            if (oldFile != null) {
                @Cleanup
                var is = oldFile.read();
                var oldData = is.readAllBytes();
                if (Arrays.equals(content, oldData)) {
                    log.info("{}: 无需更新，跳过", file);
                    return;
                }
            }
            var commitResponse = repository.createContent()
                    .path(file)
                    .branch(branchName)
                    .message("[Sparkle] 自动更新 " + file)
                    .sha(sha)
                    .content(content)
                    .commit();
            var commit = commitResponse.getCommit();
            log.info("GitHub 同步规则 “{}” 更新结果：Sha: {} 新的文件行数: {}", file, commit.getSHA1(), file.lines().count());
        } catch (Throwable e) {
            log.error("无法完成数据更新操作", e);
        }
    }


    private String generateOverDownloadIps() {
        return String.join("\n", analyseService.getOverDownloadIPAddresses().stream().map(AnalysedRule::getIp).toList());
    }

    private OffsetDateTime nowTimestamp() {
        return OffsetDateTime.now();
    }

    private OffsetDateTime pastTimestamp() {
        return OffsetDateTime.now().minus(pastInterval, ChronoUnit.MILLIS);
    }
}
