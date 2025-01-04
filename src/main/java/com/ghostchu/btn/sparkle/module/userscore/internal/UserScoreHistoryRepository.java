package com.ghostchu.btn.sparkle.module.userscore.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserScoreHistoryRepository extends SparkleCommonRepository<UserScoreHistory, Long> {

}
