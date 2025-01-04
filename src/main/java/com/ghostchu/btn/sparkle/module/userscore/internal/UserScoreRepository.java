package com.ghostchu.btn.sparkle.module.userscore.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserScoreRepository extends SparkleCommonRepository<UserScore, Long> {
    UserScore findByUser(User user);
}
