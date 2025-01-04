package com.ghostchu.btn.sparkle.module.user;

import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.ghostchu.btn.sparkle.module.user.internal.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    private final UserRepository userRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

//    @SaCheckLogin
//    public UserDto me() {
//        return (UserDto) StpUtil.getLoginId();
//    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public User getSystemUser(String moduleName) {
        return userRepository.findByEmail(moduleName.toLowerCase(Locale.ROOT) + "@sparkle.system").orElseGet(() -> {
            User user = new User();
            user.setEmail(moduleName.toLowerCase(Locale.ROOT) + "@sparkle.system");
            user.setNickname(moduleName);
            user.setRegisterAt(OffsetDateTime.now());
            user.setRandomGroup(ThreadLocalRandom.current().nextInt(9));
            user.setGithubLogin(UUID.randomUUID().toString());
            user.setGithubUserId(moduleName.hashCode() < 0 ? moduleName.hashCode() : moduleName.hashCode() * -1L);
            user.setAvatar("https://example.local/sparkle.jpg");
            user.setNickname("[SYS] " + moduleName);
            user.setLastSeenAt(OffsetDateTime.now());
            user.setLastAccessAt(OffsetDateTime.now());
            return userRepository.save(user);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<User> getUser(long id) {
        return userRepository.findById(id);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<User> getUserByGithubLogin(String githubLogin) {
        return userRepository.findByGithubLogin(githubLogin);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<User> getUserByGithubUserId(Long githubLogin) {
        return userRepository.findByGithubUserId(githubLogin);
    }

    /**
     * 从 UserDto 还原 User 对象
     *
     * @param dto UserDto
     * @return User 对象
     * @throws UserNotFoundException 尽管一般来说这不太可能为空，但如果用户被管理员干了，我们还是丢个错误
     */
    public User exchangeUserFromUserDto(UserDto dto) throws UserNotFoundException {
        var optional = userRepository.findById(dto.getId());
        if (optional.isEmpty()) {
            throw new UserNotFoundException();
        }
        return optional.get();
    }

    @Transactional
    @Lock(LockModeType.OPTIMISTIC)
    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class, OptimisticLockingFailureException.class}, backoff = @Backoff(delay = 100, multiplier = 2))
    public User saveUser(User user) {
        if (user.isSystemAccount()) {
            throw new IllegalArgumentException("User email cannot ends with @sparkle.system, it's reserved by Sparkle system.");
        }
        if (user.getNickname().startsWith("[SYS]")) {
            throw new IllegalArgumentException("User nickname cannot start with [SYS], it's reserved by Sparkle system.");
        }
        return userRepository.save(user);
    }

    @Transactional
    @Lock(LockModeType.OPTIMISTIC)
    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class, OptimisticLockingFailureException.class}, backoff = @Backoff(delay = 100, multiplier = 2))
    public User saveSystemUser(User user) {
        if (!user.isSystemAccount()) {
            throw new IllegalArgumentException("System account email must ends with @sparkle.system");
        }
        return userRepository.save(user);
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .registerAt(user.getRegisterAt())
                .lastSeenAt(user.getLastSeenAt())
                .bannedAt(user.getBannedAt())
                .bannedReason(user.getBannedReason())
                .email(user.getEmail())
                .build();
    }
}
