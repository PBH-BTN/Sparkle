package com.ghostchu.btn.sparkle.module.user;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ghostchu.btn.sparkle.exception.UserNotFoundException;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.ghostchu.btn.sparkle.module.user.internal.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

//    @SaCheckLogin
//    public UserDto me() {
//        return (UserDto) StpUtil.getLoginId();
//    }

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
        if(optional.isEmpty()){
            throw new UserNotFoundException();
        }
        return optional.get();
    }

    @Modifying
    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .avatar(user.getAvatar())
                .nickname(user.getNickname())
                .registerAt(user.getRegisterAt().getTime())
                .lastSeenAt(user.getLastSeenAt().getTime())
                .lastAccessAt(user.getLastAccessAt().getTime())
                .build();
    }
}
