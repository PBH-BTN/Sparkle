package com.ghostchu.btn.sparkle.module.githuboauth;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.audit.AuditService;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.UnirestInstance;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/auth/oauth2/github")
@Slf4j
public class OAuthController extends SparkleController {
    @Autowired
    private RedisTemplate<String, String> generalRedisTemplate;
    @Value("${oauth2.github.client-id}")
    private String clientId;
    @Value("${oauth2.github.client-secret}")
    private String clientSecret;
    @Value("${oauth2.github.scope}")
    private String scope;
    @Autowired
    private UnirestInstance unirest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserService userService;
    @Value("${sparkle.root}")
    private String serverRootUrl;
    @Autowired
    private HttpServletRequest req;
    @Autowired
    private HttpServletResponse resp;
    @Autowired
    private AuditService auditService;

    @GetMapping("/login")
    public String loginToGithub() throws IOException {
        String state = UUID.randomUUID().toString();
        String key = "github_oauth_ip:" + ip(req);
        String stateKey = "github_oauth_state:" + state;
        String jumpBack = UriComponentsBuilder.fromHttpUrl(serverRootUrl)
                .pathSegment("auth", "oauth2", "github", "callback")
                .toUriString();
        String userUri = UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", jumpBack)
                .queryParam("scope", scope)
                .queryParam("state", state).toUriString();
        // add key
        generalRedisTemplate.opsForValue().set(key, ip(req), 5, TimeUnit.MINUTES);
        generalRedisTemplate.opsForValue().set(stateKey, ip(req), 5, TimeUnit.MINUTES);
        return "redirect:" + userUri;
    }

    @GetMapping("/callback")
    @Transactional
    public String callback(Model model) throws IOException {
        String key = "github_oauth_ip:" + ip(req);
        String stateKey = "github_oauth_state:" + req.getParameter("state");
        // check if key still exists
        if (!generalRedisTemplate.hasKey(key)) {
            if (generalRedisTemplate.hasKey(stateKey)) {
                var ip = generalRedisTemplate.opsForValue().get(stateKey);
                model.addAttribute("error", new LoginError("Sparkle 登录安全保护已取消本次登录：非原始来源 OAuth2 登录回调。初始化登录的 IP 地址：" + ip + "，当前 IP 地址：" + ip(req) + "。为了阻止未授权的第三方登陆行为，本次登录已自动取消。", false));
            } else {
                model.addAttribute("error", new LoginError("Sparkle 登录安全保护已取消本次登录：未经授权的 OAuth2 登录回调。您没有从 Sparkle 开始登录流程或者完成登录流程的时间过长，为了阻止未授权的第三方登陆行为，本次登录已自动取消。", false));
            }
            return "oauth/req_github_failed";
        }
        // remove key
        generalRedisTemplate.unlink(key);
        String code = req.getParameter("code");
        if (code == null) {
            model.addAttribute("error", new LoginError("登录回调查询参数中未包含 code 字段，您是否正在手动访问 OAuth2 回调端点？", true));
            return "oauth/req_github_failed";
        }
        HttpResponse<String> re = unirest.post("https://github.com/login/oauth/access_token")
                .field("client_id", clientId)
                .field("client_secret", clientSecret)
                .field("code", code)
                .accept("application/json")
                .asString();
        if (!re.isSuccess()) {
            model.addAttribute("error", new LoginError("Github API 端点 /login/oauth/access_token 返回了未预期的响应: " + re.getStatus() + " - " + re.getStatusText() + " : " + re.getBody(), true));
            return "oauth/req_github_failed";
        }
        GithubAccessTokenCallback callback;
        try {
            callback = objectMapper.readValue(re.getBody(), GithubAccessTokenCallback.class);
        } catch (Exception e) {
            model.addAttribute("error", new LoginError("Github API 端点 /login/oauth/access_token 返回了未预期的响应: " + re.getStatus() + " - " + re.getBody(), true));
            return "oauth/req_github_failed";
        }
        HttpResponse<String> authResp = unirest.get("https://api.github.com/user")
                .header("Authorization", "Bearer " + callback.getAccessToken())
                .asString();
        if (!authResp.isSuccess() && re.getStatus() != 200) {
            model.addAttribute("error", new LoginError("无法从 Github API 端点获取数据，这可能是因为 access_token 已过期或无效: " + re.getStatus() + " - " + re.getBody(), true));
            return "oauth/req_github_failed";
        }
        GithubUserProfile userProfile = objectMapper.readValue(authResp.getBody(), GithubUserProfile.class);
        HttpResponse<String> emailResp = unirest.get("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + callback.getAccessToken())
                .asString();
        try {
            List<GithubUserEmail> userEmailList = objectMapper.readValue(emailResp.getBody(), new TypeReference<>() {
            });
            String emailSelected = userEmailList.stream().filter(GithubUserEmail::getPrimary).findFirst().orElse(new GithubUserEmail(userProfile.getLogin() + "@github-users.com", true, true)).getEmail();
            userLogin(userProfile, emailSelected);
            return "redirect:/";
        } catch (Exception e) {
            model.addAttribute("error", new LoginError("无法获取 Github 账户正在使用的邮件地址已完成与 Sparkle 账户的关联绑定: " + re.getStatus() + " - " + re.getBody(), true));
            log.error("无法获取 Github 账户正在使用的邮件地址已完成与 Sparkle 账户的关联绑定: " + re.getStatus() + " - " + re.getBody(), e);
            return "oauth/req_github_failed";
        }
    }

    private void userLogin(GithubUserProfile profile, String emailSelected) {
        User user;
        var userOptional = userService.getUserByGithubUserId(profile.getId());
        if (userOptional.isEmpty()) {
            userOptional = userService.getUserByGithubLogin(profile.getLogin());
        }
        if (userOptional.isPresent()) {
            user = userOptional.get();
        } else {
            user = new User();
            user.setEmail(emailSelected);
            user.setRegisterAt(OffsetDateTime.now());
            user.setRandomGroup(ThreadLocalRandom.current().nextInt(9));
        }
        user.setGithubLogin(profile.getLogin());
        user.setGithubUserId(profile.getId());
        user.setAvatar(profile.getAvatarUrl());
        user.setNickname(profile.getName() == null ? profile.getLogin() : profile.getName());
        user.setLastSeenAt(OffsetDateTime.now());
        user.setLastAccessAt(OffsetDateTime.now());
        user = userService.saveUser(user);
        if (user.getId() <= 0) {
            throw new IllegalStateException("用户注册失败，请联系系统管理员。");
        }
        var audit = new LinkedHashMap<String, Object>();
        audit.put("user", user.getId());
        audit.put("github", profile.getId());
        audit.put("githubLogin", profile.getLogin());
        auditService.log(req, "USER_LOGIN", true, audit);
        StpUtil.login(user.getId());
        log.info("用户 {} (ID={}, GHLogin={}, GHUID={}) 已从 {} 登录", user.getNickname(), user.getId(), profile.getLogin(), profile.getId(), ip(req));
    }

    @NoArgsConstructor
    @Data
    @AllArgsConstructor
    public static class LoginError {
        private String message;
        private boolean retryable;
    }

    @NoArgsConstructor
    @Data
    @AllArgsConstructor
    public static class GithubUserEmail {
        @JsonProperty("email")
        private String email;
        @JsonProperty("primary")
        private Boolean primary;
        @JsonProperty("verified")
        private Boolean verified;
    }

    @NoArgsConstructor
    @Data
    public static class GithubAccessTokenCallback {

        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("scope")
        private String scope;
        @JsonProperty("token_type")
        private String tokenType;
    }

    @NoArgsConstructor
    @Data
    public static class GithubUserProfile {

        @JsonProperty("login")
        private String login;
        @JsonProperty("id")
        private Long id;
        @JsonProperty("node_id")
        private String nodeId;
        @JsonProperty("avatar_url")
        private String avatarUrl;
        @JsonProperty("gravatar_id")
        private String gravatarId;
        @JsonProperty("url")
        private String url;
        @JsonProperty("html_url")
        private String htmlUrl;
        @JsonProperty("followers_url")
        private String followersUrl;
        @JsonProperty("following_url")
        private String followingUrl;
        @JsonProperty("gists_url")
        private String gistsUrl;
        @JsonProperty("starred_url")
        private String starredUrl;
        @JsonProperty("subscriptions_url")
        private String subscriptionsUrl;
        @JsonProperty("organizations_url")
        private String organizationsUrl;
        @JsonProperty("repos_url")
        private String reposUrl;
        @JsonProperty("events_url")
        private String eventsUrl;
        @JsonProperty("received_events_url")
        private String receivedEventsUrl;
        @JsonProperty("type")
        private String type;
        @JsonProperty("site_admin")
        private Boolean siteAdmin;
        @JsonProperty("name")
        private String name;
        @JsonProperty("company")
        private String company;
        @JsonProperty("blog")
        private String blog;
        @JsonProperty("location")
        private String location;
        @JsonProperty("email")
        private Object email;
        @JsonProperty("hireable")
        private Object hireable;
        @JsonProperty("bio")
        private String bio;
        @JsonProperty("twitter_username")
        private String twitterUsername;
        @JsonProperty("public_repos")
        private Integer publicRepos;
        @JsonProperty("public_gists")
        private Integer publicGists;
        @JsonProperty("followers")
        private Integer followers;
        @JsonProperty("following")
        private Integer following;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;
    }
}
