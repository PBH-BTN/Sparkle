package com.ghostchu.btn.sparkle.module.clientdiscovery;

import com.ghostchu.btn.sparkle.module.user.UserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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