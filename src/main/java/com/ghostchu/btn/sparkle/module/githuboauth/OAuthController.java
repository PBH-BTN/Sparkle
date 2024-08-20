package com.ghostchu.btn.sparkle.module.githuboauth;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.SparkleController;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.UnirestInstance;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth/oauth2/github")
@Slf4j
public class OAuthController extends SparkleController {
    private static final Cache<String, String> IP_STATE_MAPPING = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
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

    @GetMapping("/login")
    public void loginToGithub() throws IOException {
        String state = UUID.randomUUID().toString();
        String jumpBack = UriComponentsBuilder.fromHttpUrl(serverRootUrl)
                .pathSegment("auth", "oauth2", "github", "callback")
                .toUriString();
        String userUri = UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", jumpBack)
                .queryParam("scope", scope)
                .queryParam("state", state).toUriString();
        IP_STATE_MAPPING.put(ip(req), state);
        resp.sendRedirect(userUri);
    }

    @GetMapping("/callback")
    public void callback() throws IOException {
//        String state = IP_STATE_MAPPING.getIfPresent(peerIp);
//        if (state == null) {
//            throw new IllegalStateException("Seems you didn't login to this BTN instance via login entrypoint but received login callback. Cross-site attack? Try again!");
//        }
        IP_STATE_MAPPING.invalidate(ip(req));
        String code = req.getParameter("code");
        if (code == null) {
            throw new IllegalStateException("The login callback didn't contains code field.");
        }
        HttpResponse<String> re = unirest.post("https://github.com/login/oauth/access_token")
                .field("client_id", clientId)
                .field("client_secret", clientSecret)
                .field("code", code)
                .accept("application/json")
                .asString();
        if (!re.isSuccess()) {
            throw new IllegalStateException("The login callback returns incorrect response: " + re.getStatus() + " - " + re.getStatusText() + " : " + re.getBody());
        }
        GithubAccessTokenCallback callback = objectMapper.readValue(re.getBody(), GithubAccessTokenCallback.class);
        HttpResponse<String> authResp = unirest.get("https://api.github.com/user")
                .header("Authorization", "Bearer " + callback.getAccessToken())
                .asString();
        if (!authResp.isSuccess()) {
            throw new IllegalStateException("An error occurred when requesting user Github profile via access token: " + re.getStatus() + " - " + re.getStatusText() + " : " + re.getBody());
        }
        GithubUserProfile userProfile = objectMapper.readValue(authResp.getBody(), GithubUserProfile.class);
        HttpResponse<String> emailResp = unirest.get("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + callback.getAccessToken())
                .asString();
        List<GithubUserEmail> userEmailList = objectMapper.readValue(emailResp.getBody(), new TypeReference<>() {
        });
        String emailSelected = userEmailList.stream().filter(GithubUserEmail::getPrimary).findFirst().orElse(new GithubUserEmail(userProfile.getLogin() + "@github-users.com", true, true)).getEmail();
        userLogin(userProfile, emailSelected);
        resp.sendRedirect("/");
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
            user.setRegisterAt(new Timestamp(System.currentTimeMillis()));
        }
        user.setGithubLogin(profile.getLogin());
        user.setGithubUserId(profile.getId());
        user.setAvatar(profile.getAvatarUrl());
        user.setNickname(profile.getName() == null ? profile.getLogin() : profile.getName());
        user.setLastSeenAt(new Timestamp(System.currentTimeMillis()));
        user.setLastAccessAt(new Timestamp(System.currentTimeMillis()));
        user = userService.saveUser(user);
        if (user.getId() <= 0) {
            throw new IllegalStateException("用户注册失败，请联系系统管理员。");
        }
        StpUtil.login(user.getId());
        log.info("用户 {} (ID={}, GHLogin={}, GHUID={}) 已从 {} 登录", user.getNickname(), user.getId(), profile.getLogin(), profile.getId(), ip(req));
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
