package com.ghostchu.btn.sparkle.module.githubupdate;

import com.ghostchu.btn.sparkle.module.banhistory.BanHistoryService;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistory;
import com.ghostchu.btn.sparkle.module.banhistory.internal.BanHistoryRepository;
import com.ghostchu.btn.sparkle.module.rule.RuleService;
import com.ghostchu.btn.sparkle.util.MsgUtil;
import jakarta.transaction.Transactional;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.StringJoiner;

@Service
@Slf4j
public class GithubUpdateService {
    private final BanHistoryRepository banHistoryRepository;
    private final RuleService ruleService;
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

    public GithubUpdateService(RuleService ruleService, BanHistoryService banHistoryService,
                               BanHistoryRepository banHistoryRepository) {
        this.ruleService = ruleService;
        this.banHistoryService = banHistoryService;
        this.banHistoryRepository = banHistoryRepository;
    }


    @Scheduled(fixedRateString = "${service.githubruleupdate.interval}")
    @Transactional
    public void githubRuleUpdate() throws IOException {
        log.info("开始更新 GitHub 同步规则存储库...");
        GitHub github = new GitHubBuilder().withOAuthToken(accessToken, orgName).build();
        var organization = github.getOrganization(orgName);
        if (organization == null) {
            throw new IllegalArgumentException("Organization " + orgName + " not found");
        }
        var repository = organization.getRepository(repoName);
        updateFile(repository, "untrusted-ips.txt", generateUntrustedIps().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "hp_torrent.txt", generateHpTorrents().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "dt_torrent.txt", generateDtTorrents().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "go.torrent dev 20181121.txt", generateBaiduNetdisk().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "0xde-0xad-0xbe-0xef.txt", generateDeadBeef().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "123pan.txt", generate123pan().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "random-peerid.txt", generateGopeedDev().getBytes(StandardCharsets.UTF_8));
        updateFile(repository, "dot1_v6_tagging.txt", generateDot1IPV6().getBytes(StandardCharsets.UTF_8));
    }

    private String generateDot1IPV6() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "Transmission 2.96",
                        pastTimestamp(),
                        nowTimestamp()
                )
                .stream()
                .filter(banHistory -> banHistory.getPeerIp().getHostAddress().endsWith("::1"))
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .distinct()
                .forEach(banHistory -> strJoiner.add(banHistory.getPeerIp().getHostAddress()));
        return strJoiner.toString();
    }

    private String generateGopeedDev() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                        "%Gopeed dev%",
                        pastTimestamp(),
                        nowTimestamp()
                ).stream()
                .filter(banHistory -> !banHistory.getPeerId().toLowerCase(Locale.ROOT).startsWith("-gp"))
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generate123pan() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                "%offline-download (devel) (anacrolix/torrent unknown)%",
                pastTimestamp(),
                nowTimestamp()
        )
                .stream()
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateDeadBeef() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                "%ޭ__%",
                pastTimestamp(),
                nowTimestamp()
        )
                .stream()
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateBaiduNetdisk() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerClientNameLikeAndInsertTimeBetween(
                "go.torrent dev 20181121%",
                pastTimestamp(),
                nowTimestamp()
        )
                .stream()
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }


    private String generateDtTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerIdLikeOrPeerClientNameLike(
                "-DT%",
                "dt/torrent%",
                pastTimestamp(),
                nowTimestamp()
        ).stream()
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private String generateHpTorrents() {
        var strJoiner = new StringJoiner("\n");
        banHistoryRepository.findDistinctByPeerIdLikeOrPeerClientNameLike(
                "-HP%",
                "hp/torrent%",
                pastTimestamp(),
                nowTimestamp()

        )
                .stream()
                .sorted(Comparator.comparing(o -> o.getPeerIp().getHostAddress()))
                .map(history->history.getPeerIp().getHostAddress())
                .distinct()
                .forEach(strJoiner::add);
        return strJoiner.toString();
    }

    private void updateFile(GHRepository repository, String file, byte[] content) {
        try {
            var oldFile = repository.getFileContent(file);
            var sha = oldFile != null ? oldFile.getSha() : null;

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
            log.info("GitHub 同步规则 “{}” 更新结果：Sha: {}", file, commit.getSHA1());
        } catch (Exception e) {
            log.error("无法完成数据更新操作", e);
        }
    }

    private String generateUntrustedIps() {
        var strJoiner = new StringJoiner("\n");
        banHistoryService.generateUntrustedIPAddresses()
                .stream().map(InetAddress::getHostAddress)
                .forEach(strJoiner::add);
        log.info("已生成规则：{} 条", strJoiner.toString().lines().count());
        return strJoiner.toString();
    }

    private Timestamp nowTimestamp() {
        return new Timestamp(System.currentTimeMillis());
    }

    private Timestamp pastTimestamp() {
        return new Timestamp(System.currentTimeMillis() - pastInterval);
    }
}
