package com.ghostchu.btn.sparkle.clientdiscovery;

import com.ghostchu.btn.sparkle.user.UserDto;
import com.ghostchu.btn.sparkle.user.internal.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientDiscoveryDto {
    private Long hash;
    private String clientName;
    private String peerId;
    private Long foundAt;
    private UserDto foundBy;
    private Long lastSeenAt;
    private UserDto lastSeenBy;
}
