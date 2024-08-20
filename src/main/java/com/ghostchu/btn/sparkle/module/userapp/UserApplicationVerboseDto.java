package com.ghostchu.btn.sparkle.module.userapp;

import com.ghostchu.btn.sparkle.module.user.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserApplicationVerboseDto implements Serializable {
    private Long id;
    private String appId;
    private String appSecret;
    private String comment;
    private Long createdAt;
    private UserDto user;
}
