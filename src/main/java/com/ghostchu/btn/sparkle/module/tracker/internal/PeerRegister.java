package com.ghostchu.btn.sparkle.module.tracker.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerRegister {
    private InetAddress reqIp;
    private byte[] peerId;
    private InetAddress peerIp;
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
