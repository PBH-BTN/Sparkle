package com.ghostchu.btn.sparkle.module.user;

import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.ghostchu.btn.sparkle.module.user.internal.UserRepository;
import com.ghostchu.btn.sparkle.util.TimeUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoField;
import java.util.Optional;

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

    @Scheduled(fixedDelayString = "${service.user.metrics-interval}")
    @Transactional
    public void updateUserMetrics() {
        meterRegistry.gauge("sparkle_user_total", userRepository.count());
        meterRegistry.gauge("sparkle_user_active", userRepository.countUserByLastSeenAtAfter(TimeUtil.toUTC(System.currentTimeMillis() - 86400000)));
    }


    public Optional<User> getUser(long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByGithubLogin(String githubLogin) {
        return userRepository.findByGithubLogin(githubLogin);
    }

    public Optional<User> getUserByGithubUserId(Long githubLogin) {
        return userRepository.findByGithubUserId(githubLogin);
    }

    /**
     * 从 UserDto 还原 User 对象
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

    @Modifying
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .registerAt(user.getRegisterAt().getLong(ChronoField.MILLI_OF_DAY))
                .lastSeenAt(user.getLastSeenAt().getLong(ChronoField.MILLI_OF_DAY))
                .banned(user.getBanned())
                .build();
    }
}
