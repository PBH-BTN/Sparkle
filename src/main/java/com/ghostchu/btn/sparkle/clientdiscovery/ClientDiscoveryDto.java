package com.ghostchu.btn.sparkle.clientdiscovery;

import com.ghostchu.btn.sparkle.user.internal.User;

import java.sql.Timestamp;

public class ClientDiscoveryDto {
    private Long hash;
    private String clientName;
    private String peerId;
    private Timestamp foundAt;
    private User foundBy;
    private Timestamp lastSeenAt;
    private User lastSeenBy;
}
