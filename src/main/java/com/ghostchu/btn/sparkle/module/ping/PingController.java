package com.ghostchu.btn.sparkle.module.ping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.module.audit.AuditService;
import com.ghostchu.btn.sparkle.module.ping.ability.impl.*;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnPeerHistoryPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnPeerPing;
import com.ghostchu.btn.sparkle.module.ping.dto.BtnRule;
import com.ghostchu.btn.sparkle.module.userapp.UserApplicationService;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import com.ghostchu.btn.sparkle.util.IPUtil;
import com.ghostchu.btn.sparkle.util.ServletUtil;
import com.ghostchu.btn.sparkle.util.ipdb.GeoIPManager;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ping")
@Transactional
@Slf4j
public class PingController extends SparkleController {
    @Autowired
    private PingService service;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HttpServletRequest req;
    @Autowired
    private PingService pingService;
    @Autowired
    private SubmitPeersAbility submitPeersAbility;
    @Autowired
    private SubmitBansAbility submitBansAbility;
    @Autowired
    private ReconfigureAbility reconfigureAbility;
    @Autowired
    private CloudRuleAbility cloudRuleAbility;
    @Autowired
    private UserApplicationService userApplicationService;
    @Autowired
    private AuditService auditService;
    @Value("${sparkle.root}")
    private String sparkleRoot;
    @Value("${sparkle.root.china}")
    private String sparkleRootChina;
    @Autowired
    private GeoIPManager geoIPManager;
    @Autowired
    private SubmitHistoriesAbility submitHistoriesAbility;


    @PostMapping("/peers/submit")
    public ResponseEntity<String> submitPeers(@RequestBody @Validated BtnPeerPing ping) throws AccessDeniedException {
//        var cred = cred();
//        var audit = new LinkedHashMap<String, Object>();
//        audit.put("appId", cred.getAppId());
//        if (isCredBanned(cred)) {
//            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求提交 Peers 数据：(AppId={}, AppSecret={}, UA={})",
//                    ip(req), cred.getAppId(), cred.getAppSecret(), ua(req));
//            audit.put("error", "UserApplication Banned");
//            auditService.log(req, "BTN_PEERS_SUBMIT", false, audit);
//            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
//        }
//        IPAddress ip = new IPAddressString(ip(req)).getAddress();
//        var handled = service.handlePeers(ip.toInetAddress(), cred, ping);
//        log.info("[OK] [Ping] [{}] 已提交 {}/{} 个 Peers 信息：(AppId={}, UA={})",
//                ip(req), ping.getPeers().size(), handled, cred.getAppId(), ua(req));
//        audit.put("peers_size", ping.getPeers().size());
//        audit.put("peers_handled", handled);
//        auditService.log(req, "BTN_PEERS_SUBMIT", true, audit);
        return ResponseEntity.status(200).build();
    }

    @PostMapping("/histories/submit")
    public ResponseEntity<String> submitPeerHistories(@RequestBody @Validated BtnPeerHistoryPing ping) throws AccessDeniedException {
        var cred = cred();
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", cred.getAppId());
        if (isCredBanned(cred)) {
            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求提交 Histories 数据：(AppId={}, AppSecret={}, UA={})",
                    ip(req), cred.getAppId(), cred.getAppSecret(), ua(req));
            audit.put("error", "UserApplication Banned");
            auditService.log(req, "BTN_HISTORY_SUBMIT", false, audit);
            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
        }
        IPAddress ip = IPUtil.toIPAddress(ip(req));
        var handled = service.handlePeerHistories(ip.toInetAddress(), cred, ping);
        log.info("[OK] [Ping] [{}] 已提交 {}/{} 个 PeerHistory 信息：(AppId={}, UA={})",
                ip(req), ping.getPeers().size(), handled, cred.getAppId(), ua(req));
        audit.put("peers_size", ping.getPeers().size());
        audit.put("peers_handled", handled);
        auditService.log(req, "BTN_HISTORY_SUBMIT", true, audit);
        return ResponseEntity.status(200).build();
    }

    @PostMapping("/bans/submit")
    public ResponseEntity<String> submitBans(@RequestBody @Validated BtnBanPing ping) throws AccessDeniedException {
        var cred = cred();
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", cred.getAppId());
        if (isCredBanned(cred)) {
            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求提交 Bans 数据：(AppId={},  UA={})",
                    ip(req), cred.getAppId(), ua(req));
            audit.put("error", "UserApplication Banned");
            auditService.log(req, "BTN_BANS_SUBMIT", false, audit);
            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
        }
        IPAddress ip = IPUtil.toIPAddress(ip(req));
        var handled = service.handleBans(ip.toInetAddress(), cred, ping);
        log.info("[OK] [Ping] [{}] 已提交 {}/{} 个 封禁信息：(AppId={}, UA={})",
                ip(req), ping.getBans().size(), handled, cred.getAppId(), ua(req));
        audit.put("bans_size", ping.getBans().size());
        audit.put("bans_handled", handled);
        auditService.log(req, "BTN_PEERS_SUBMIT", true, audit);
        return ResponseEntity.status(200).build();
    }

    @GetMapping("/config")
    public ResponseEntity<Object> config() throws AccessDeniedException, JsonProcessingException, UnknownHostException {
        var cred = cred();
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", cred.getAppId());
        if (isCredBanned(cred)) {
            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求配置文件：(AppId={}, UA={})",
                    ip(req), cred.getAppId(), ua(req));
            audit.put("error", "UserApplication Banned");
            auditService.log(req, "BTN_CONFIG", false, audit);
            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
        }
        log.info("[OK] [Config] [{}] 响应配置元数据 (AppId={}, UA={})",
                ip(req), cred.getAppId(), ua(req));
        Map<String, Object> rootObject = new LinkedHashMap<>();
        rootObject.put("min_protocol_version", pingService.getMinProtocolVersion());
        rootObject.put("max_protocol_version", pingService.getMaxProtocolVersion());

        Map<String, Object> abilityObject = new LinkedHashMap<>();
        rootObject.put("ability", abilityObject);
        //abilityObject.put("submit_peers", submitPeersAbility);
        abilityObject.put("submit_bans", submitBansAbility);
        abilityObject.put("submit_histories", submitHistoriesAbility);
        abilityObject.put("reconfigure", reconfigureAbility);
        abilityObject.put("rules", cloudRuleAbility);
        auditService.log(req, "BTN_CONFIG", true, audit);
        var json = objectMapper.writeValueAsString(rootObject);
        var countryIso = geoIPManager.geoData(InetAddress.getByName(ip(req))).getCountryIso();
        if (countryIso != null && countryIso.equalsIgnoreCase("CN")) {
            json = json.replace(sparkleRoot, sparkleRootChina);
        }
        return ResponseEntity.ok().body(json);
    }

    @GetMapping("/rules/retrieve")
    public ResponseEntity<String> rule() throws IOException, AccessDeniedException {
        var cred = cred();
        var audit = new LinkedHashMap<String, Object>();
        audit.put("appId", cred.getAppId());
        if (isCredBanned(cred)) {
            log.warn("[BANNED] [Ping] [{}] 正在以遭到封禁的 UserApplication 请求云端规则：(AppId={}, UA={})",
                    ip(req), cred.getAppId(), ua(req));
            audit.put("error", "UserApplication Banned");
            auditService.log(req, "BTN_RULES_RETRIEVE", false, audit);
            return ResponseEntity.status(403).body("UserApplication 已被管理员封禁，请与服务器管理员联系");
        }
        String version = req.getParameter("rev");
        BtnRule btn = service.generateBtnRule();
        String rev = Hashing.goodFastHash(32).hashString(objectMapper.writeValueAsString(btn), StandardCharsets.UTF_8).toString();
        if (rev.equals(version)) {
            log.info("[OK] [Rule] [{}] 规则无变化，响应 204 状态码 (AppId={}, AppSecret={}, UA={})",
                    ip(req), cred.getAppId(), cred.getAppSecret(), ua(req));
            return ResponseEntity.status(204).build();
        }
        btn.setVersion(rev);
        log.info("[OK] [Rule] [{}] 已发送新的规则 {} -> {} (AppId={}, AppSecret={}, UA={})",
                ip(req), version, rev, cred.getAppId(), cred.getAppSecret(), ua(req));
        audit.put("from", version);
        audit.put("to", rev);
        auditService.log(req, "BTN_RULES_RETRIEVE", true, audit);
        return ResponseEntity.status(200).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(btn));
    }

    private void checkIfInvalidPBH() throws AccessDeniedException {
        String ua = req.getHeader("User-Agent");
    }

    public boolean isCredBanned(UserApplication userApplication) {
        return userApplication.getBanned() || userApplication.getUser().getBanned();
    }

    private UserApplication cred() throws AccessDeniedException {
        ClientAuthenticationCredential cred = ServletUtil.getAuthenticationCredential(req);
        if (!cred.isValid()) {
            log.warn("[FAIL] [UserApp] [{}] Credential not provided.", ip(req));
            throw new AccessDeniedException("UserApplication 鉴权失败：请求中未包含凭据信息。");
        }
        var userAppOptional = userApplicationService.getUserApplication(cred.appId(), cred.appSecret());
        if (userAppOptional.isEmpty()) {
//            log.warn("[FAIL] [UserApp] [{}] UserApplication (AppId={}, AppSecret={}) are not exists.",
//                    ip(req), cred.appId(), cred.appSecret());
            throw new AccessDeniedException("UserApplication 鉴权失败：指定的用户应用程序不存在，这可能是因为：" +
                                            "(1)未配置 AppId/AppSecret 或配置不正确 (2)您重置了 AppSecret 但忘记在客户端中更改 (3)用户应用程序被管理员停用或删除，请检查。");
        }
        return userAppOptional.get();
    }
}
