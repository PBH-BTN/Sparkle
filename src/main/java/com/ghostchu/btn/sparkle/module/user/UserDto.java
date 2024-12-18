package com.ghostchu.btn.sparkle.module.user;

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
/**
 * UserDto 可能会暴露给外部接口，禁止包含敏感数据
 */
public class UserDto implements Serializable {
    private Long id;
    private String avatar;
    private String nickname;
    private OffsetDateTime registerAt;
    private OffsetDateTime lastSeenAt;
    private OffsetDateTime bannedAt;
    private String bannedReason;
}
