package com.ghostchu.btn.sparkle.userapp.internal;

import com.ghostchu.btn.sparkle.repository.SparkleCommonRepository;
import com.ghostchu.btn.sparkle.user.internal.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApplicationRepository extends SparkleCommonRepository<UserApplication, Long> {

    List<UserApplication> findByUser_Id(Long id);

    Optional<UserApplication> findByAppId(String appId);

    Optional<UserApplication> findByAppIdAndAppSecret(String appId, String appSecret);

    List<UserApplication> findByUser(User user);

    long countByUser(User user);
}
