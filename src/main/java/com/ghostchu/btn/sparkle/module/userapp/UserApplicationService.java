package com.ghostchu.btn.sparkle.module.userapp;

import com.ghostchu.btn.sparkle.exception.TooManyUserApplicationException;
import com.ghostchu.btn.sparkle.exception.UserApplicationNotFoundException;
import com.ghostchu.btn.sparkle.module.user.UserService;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplicationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserApplicationService {
    private final UserApplicationRepository userApplicationRepository;
    private final UserService userService;
    private final long userMaxApps;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RedisTemplate<String, String> longRedisTemplate;

    public UserApplicationService(UserApplicationRepository userApplicationRepository
            , UserService userService
            , @Value("${service.userapplication.user-max-apps}") long userMaxApps) {
        this.userApplicationRepository = userApplicationRepository;
        this.userService = userService;
        this.userMaxApps = userMaxApps;
    }

    /**
     * 推荐使用此方法获取 UserApplication，因为它同时会用 appSecret 作为检索条件，相当于进行了鉴权
     *
     * @param appId     AppId
     * @param appSecret AppSecret
     * @return UserApplicationDto
     */

    public Optional<UserApplication> getUserApplication(String appId, String appSecret) {
        return userApplicationRepository.findByAppIdAndAppSecret(appId, appSecret);
    }

    /**
     * 此方法应该仅用于程序内部使用
     *
     * @param appId AppId
     * @return UserApplicationDto
     */
    public Optional<UserApplication> getUserApplication(String appId) {
        return userApplicationRepository.findByAppId(appId);
    }

    public List<UserApplication> getUserApplications(User user) {
        return userApplicationRepository.findByUser(user);
    }

    public void setUserApplicationLastAccess(long userAppId) {
        longRedisTemplate.opsForValue().set("userapp_recent_active:" + userAppId, String.valueOf(System.currentTimeMillis()));
    }

    public long getUserApplicationLastAccess(long userAppId) {
        Object object = longRedisTemplate.opsForValue().get("userapp_recent_active:" + userAppId);
        if (object == null) return -1;
        return Long.parseLong(object.toString());
    }

    @Modifying
    @Transactional
    public UserApplication generateUserApplicationForUser(User user, String comment, OffsetDateTime createdAt) throws TooManyUserApplicationException {
        long userOwnedApps = userApplicationRepository.countByUser(user);
        if (userOwnedApps >= userMaxApps) {
            throw new TooManyUserApplicationException();
        }
        UserApplication userApplication = new UserApplication();
        userApplication.setId(null);
        userApplication.setAppId(UUID.randomUUID().toString());
        userApplication.setAppSecret(UUID.randomUUID().toString());
        userApplication.setComment(comment);
        userApplication.setCreatedAt(createdAt);
        userApplication.setUser(user);
        return userApplicationRepository.save(userApplication);
    }

    @Modifying
    @Transactional
    public UserApplication resetUserApplicationSecret(Long id) throws UserApplicationNotFoundException {
        Optional<UserApplication> userApplication = userApplicationRepository.findById(id);
        if (userApplication.isEmpty()) {
            throw new UserApplicationNotFoundException();
        }
        var usrApp = userApplication.get();
        usrApp.setAppSecret(UUID.randomUUID().toString());
        return userApplicationRepository.save(usrApp);
    }

    @Modifying
    @Transactional
    public void deleteUserApplication(Long id) throws UserApplicationNotFoundException {
        Optional<UserApplication> userApplication = userApplicationRepository.findById(id);
        if (userApplication.isEmpty()) {
            throw new UserApplicationNotFoundException();
        }
        var userApp = userApplication.get();
        userApp.setDeletedAt(OffsetDateTime.now());
        userApplicationRepository.save(userApp);
    }

    @Modifying
    @Transactional
    public UserApplication editUserApplicationComment(Long id, String comment) throws UserApplicationNotFoundException {
        Optional<UserApplication> userApplication = userApplicationRepository.findById(id);
        if (userApplication.isEmpty()) {
            throw new UserApplicationNotFoundException();
        }
        var usrApp = userApplication.get();
        usrApp.setComment(comment);
        return userApplicationRepository.save(usrApp);
    }

    public UserApplicationDto toDto(UserApplication userApplication) {
        long lastAccessAt = getUserApplicationLastAccess(userApplication.getId());
        OffsetDateTime lastAccessAtTime = lastAccessAt == -1 ? null : OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(lastAccessAt), java.time.ZoneOffset.UTC);
        return UserApplicationDto.builder()
                .id(userApplication.getId())
                .user(userService.toDto(userApplication.getUser()))
                .comment(userApplication.getComment())
                .appId(userApplication.getAppId())/**/
                .createdAt(userApplication.getCreatedAt())
                .bannedAt(userApplication.getBannedAt())
                .bannedReason(userApplication.getBannedReason())
                .lastAccessAt(lastAccessAtTime)
                .build();
    }


    public UserApplicationVerboseDto toVerboseDto(UserApplication userApplication) {
        return UserApplicationVerboseDto.builder()
                .id(userApplication.getId())
                .user(userService.toDto(userApplication.getUser()))
                .comment(userApplication.getComment())
                .appId(userApplication.getAppId())
                .appSecret(userApplication.getAppSecret())
                .createdAt(userApplication.getCreatedAt())
                .build();
    }
}
