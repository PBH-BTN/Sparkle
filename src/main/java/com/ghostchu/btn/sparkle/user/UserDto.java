package com.ghostchu.btn.sparkle.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/**
 * UserDto 可能会暴露给外部接口，禁止包含敏感数据
 */
public class UserDto {
    private Long id;
    private String avatar;
    private String nickname;
    private Long registerAt;
    private Long lastSeenAt;
    private Long lastAccessAt;
}
