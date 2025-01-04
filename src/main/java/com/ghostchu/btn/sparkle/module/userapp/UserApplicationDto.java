package com.ghostchu.btn.sparkle.module.userapp;

import com.ghostchu.btn.sparkle.module.user.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserApplicationDto implements Serializable {
    private Long id;
    private String appId;
    private String comment;
    private OffsetDateTime createdAt;
    private UserDto user;
    private OffsetDateTime bannedAt;
    private String bannedReason;
    private OffsetDateTime lastAccessAt;
}
