package com.ghostchu.btn.sparkle.module.clientdiscovery;

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
public class ClientDiscoveryDto implements Serializable {
    private Long hash;
    private String clientName;
    private String peerId;
    private OffsetDateTime foundAt;
}
