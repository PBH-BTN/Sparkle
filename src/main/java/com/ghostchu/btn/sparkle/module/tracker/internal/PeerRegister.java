package com.ghostchu.btn.sparkle.module.tracker.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class PeerRegister {
    //    private InetAddress reqIp;
    private byte[] peerId;
    private byte[] peerIp;
    private int peerPort;
    private long uploadedOffset;
    private long uploaded;
    private long downloadedOffset;
    private long downloaded;
    private long left;
    //private PeerEvent lastEvent;
    //private String userAgent;
    private long lastTimeSeen;
    private short numWant;
}
