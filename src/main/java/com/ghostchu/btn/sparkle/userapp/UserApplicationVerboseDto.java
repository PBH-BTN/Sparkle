package com.ghostchu.btn.sparkle.userapp;

import com.ghostchu.btn.sparkle.user.UserDto;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserApplicationVerboseDto  {
    private Long id;
    private String appId;
    private String appSecret;
    private String comment;
    private Long createdAt;
    private UserDto user;
}
