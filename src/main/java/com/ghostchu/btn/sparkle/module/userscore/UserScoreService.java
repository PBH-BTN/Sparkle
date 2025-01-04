package com.ghostchu.btn.sparkle.module.userscore;

import com.ghostchu.btn.sparkle.module.user.internal.User;
import com.ghostchu.btn.sparkle.module.userscore.internal.UserScore;
import com.ghostchu.btn.sparkle.module.userscore.internal.UserScoreHistory;
import com.ghostchu.btn.sparkle.module.userscore.internal.UserScoreHistoryRepository;
import com.ghostchu.btn.sparkle.module.userscore.internal.UserScoreRepository;
import jakarta.persistence.LockModeType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class UserScoreService {
    private final UserScoreRepository userScoreRepository;
    private final UserScoreHistoryRepository userScoreHistoryRepository;

    public UserScoreService(UserScoreRepository userScoreRepository, UserScoreHistoryRepository userScoreHistoryRepository) {
        this.userScoreRepository = userScoreRepository;
        this.userScoreHistoryRepository = userScoreHistoryRepository;
    }

    public long getUserScoreBytes(User user) {
        UserScore userScore = userScoreRepository.findByUser(user);
        if (userScore != null) {
            return userScore.getScoreBytes();
        } else {
            return 0L;
        }
    }

    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class, OptimisticLockingFailureException.class}, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    @Lock(value = LockModeType.OPTIMISTIC)
    public void addUserScoreBytes(User user, long changes, String reason) {
        UserScore userScore = userScoreRepository.findByUser(user);
        if (userScore != null) {
            userScore.setScoreBytes(userScore.getScoreBytes() + changes);
        } else {
            userScore = new UserScore(null, user, changes, 0L);
        }
        userScoreRepository.save(userScore);
        userScoreHistoryRepository.save(new UserScoreHistory(null, OffsetDateTime.now(), user, changes, userScore.getScoreBytes(), reason));
    }
}
