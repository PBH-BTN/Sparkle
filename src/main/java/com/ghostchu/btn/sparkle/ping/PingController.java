package com.ghostchu.btn.sparkle.ping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.exception.AccessDeniedException;
import com.ghostchu.btn.sparkle.ping.ability.impl.CloudRuleAbility;
import com.ghostchu.btn.sparkle.ping.ability.impl.ReconfigureAbility;
import com.ghostchu.btn.sparkle.ping.ability.impl.SubmitBansAbility;
import com.ghostchu.btn.sparkle.ping.ability.impl.SubmitPeersAbility;
import com.ghostchu.btn.sparkle.ping.dto.BtnBanPing;
import com.ghostchu.btn.sparkle.ping.dto.BtnPeerPing;
import com.ghostchu.btn.sparkle.ping.dto.BtnRule;
import com.ghostchu.btn.sparkle.userapp.UserApplicationService;
import com.ghostchu.btn.sparkle.userapp.internal.UserApplication;
import com.ghostchu.btn.sparkle.util.ServletUtil;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ping")
@Transactional
@Slf4j
public class PingController {
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

    @PostMapping("/peers/submit")
    public ResponseEntity<String> submitPeers(@RequestBody @Validated BtnPeerPing ping) throws AccessDeniedException {
        var cred = cred();
        IPAddress ip = new IPAddressString(ServletUtil.getIP(req)).getAddress();
        var handled = service.handlePeers(ip.toInetAddress(), cred, ping);
        log.info("[OK] [Ping] [{}] 已提交 {}/{} 个 Peers 信息：(AppId={}, AppSecret={}, UA={})",
                ServletUtil.getIP(req), ping.getPeers().size(), handled, cred.getAppId(), cred.getAppSecret(), req.getHeader("User-Agent"));
        return ResponseEntity.status(200).build();
    }

    @PostMapping("/bans/submit")
    public ResponseEntity<String> submitBans(@RequestBody @Validated BtnBanPing ping) throws AccessDeniedException {
        var cred = cred();
        IPAddress ip = new IPAddressString(ServletUtil.getIP(req)).getAddress();
        var handled = service.handleBans(ip.toInetAddress(), cred, ping);
        log.info("[OK] [Ping] [{}] 已提交 {}/{} 个 封禁信息：(AppId={}, AppSecret={}, UA={})",
                ServletUtil.getIP(req), ping.getBans().size(), handled, cred.getAppId(), cred.getAppSecret(), req.getHeader("User-Agent"));
        return ResponseEntity.status(200).build();
    }

    @GetMapping("/config")
    public Map<String, Object> config() throws AccessDeniedException {
        var cred = cred();
        log.info("[OK] [Config] [{}] 响应配置元数据 (AppId={}, AppSecret={}, UA={})",
                ServletUtil.getIP(req), cred.getAppId(), cred.getAppSecret(), req.getHeader("User-Agent"));
        Map<String, Object> rootObject = new LinkedHashMap<>();
        rootObject.put("min_protocol_version", pingService.getMinProtocolVersion());
        rootObject.put("max_protocol_version", pingService.getMaxProtocolVersion());
        Map<String, Object> abilityObject = new LinkedHashMap<>();
        rootObject.put("ability", abilityObject);
        abilityObject.put("submit_peers", submitPeersAbility);
        abilityObject.put("submit_bans", submitBansAbility);
        abilityObject.put("reconfigure", reconfigureAbility);
        abilityObject.put("rules", cloudRuleAbility);
        return rootObject;
    }

    @GetMapping("/rules/retrieve")
    public ResponseEntity<String> rule() throws IOException, AccessDeniedException {
        var cred = cred();
        String version = req.getParameter("rev");
        BtnRule btn = service.generateBtnRule();
        String rev = Hashing.goodFastHash(32).hashString(objectMapper.writeValueAsString(btn), StandardCharsets.UTF_8).toString();
        if (rev.equals(version)) {
            log.info("[OK] [Rule] [{}] 规则无变化，响应 204 状态码 (AppId={}, AppSecret={}, UA={})",
                    ServletUtil.getIP(req), cred.getAppId(), cred.getAppSecret(), req.getHeader("User-Agent"));
            return ResponseEntity.status(204).build();
        }
        btn.setVersion(rev);
        log.info("[OK] [Rule] [{}] 已发送新的规则 {} -> {} (AppId={}, AppSecret={}, UA={})",
                ServletUtil.getIP(req), version, rev, cred.getAppId(), cred.getAppSecret(), req.getHeader("User-Agent"));
        return ResponseEntity.status(200).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(btn));
    }

    private void checkIfInvalidPBH() throws AccessDeniedException {
        String ua = req.getHeader("User-Agent");
    }

    private UserApplication cred() throws AccessDeniedException {
        ClientAuthenticationCredential cred = ServletUtil.getAuthenticationCredential(req);
        if (!cred.isValid()) {
            log.warn("[FAIL] [UserApp] [{}] Credential not provided.", ServletUtil.getIP(req));
            throw new AccessDeniedException("UserApplication 鉴权失败：请求中未包含凭据信息。");
        }
        var userAppOptional = userApplicationService.getUserApplication(cred.appId(), cred.appSecret());
        if (userAppOptional.isEmpty()) {
            log.warn("[FAIL] [UserApp] [{}] UserApplication (AppId={}, AppSecret={}) are not exists.",
                    ServletUtil.getIP(req), cred.appId(), cred.appSecret());
            throw new AccessDeniedException("UserApplication 鉴权失败：指定的用户应用程序不存在，这可能是因为：" +
                    "(1)未配置 AppId/AppSecret 或配置不正确 (2)您重置了 AppSecret 但忘记在客户端中更改 (3)用户应用程序被管理员停用或删除，请检查。");
        }
        return userAppOptional.get();
    }
}
