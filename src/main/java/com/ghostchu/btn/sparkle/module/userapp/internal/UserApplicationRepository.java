package com.ghostchu.btn.sparkle.module.userapp.internal;

import com.ghostchu.btn.sparkle.module.repository.SparkleCommonRepository;
import com.ghostchu.btn.sparkle.module.user.internal.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApplicationRepository extends SparkleCommonRepository<UserApplication, Long> {
    @Query("select ua from UserApplication ua where ua.user.id = ?1 and ua.deletedAt is null")
    List<UserApplication> findByUser_Id(Long id);

    @Query("select ua from UserApplication ua where ua.appId = ?1 and ua.deletedAt is null")
    Optional<UserApplication> findByAppId(String appId);

    @Query("select ua from UserApplication ua where ua.appId = ?1 and ua.appSecret = ?2 and ua.deletedAt is null")
    Optional<UserApplication> findByAppIdAndAppSecret(String appId, String appSecret);

    @Query("select ua from UserApplication ua where ua.user = ?1 and ua.deletedAt is null")
    List<UserApplication> findByUser(User user);

    @Query("select count(ua) from UserApplication ua where ua.user = ?1 and ua.deletedAt is null")
    long countByUser(User user);


}
