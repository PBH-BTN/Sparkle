package com.ghostchu.btn.sparkle.module.user.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends SparkleCommonRepository<User, Long> {
    Optional<User> findByGithubLogin(@NonNull String githubLogin);
    Optional<User> findByGithubUserId(@NonNull Long githubUserId);
    List<User> findByNickname(String nickname);
}
