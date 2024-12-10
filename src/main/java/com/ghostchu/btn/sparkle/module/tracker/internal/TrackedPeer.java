package com.ghostchu.btn.sparkle.module.tracker.internal;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Data
public class TrackedPeer {
    private String peerId;
    private String reqIp;
    private String peerIp;
    private int peerPort;
    private boolean seeder;
    private String userAgent;
    public String toKey() {
        return infoHash + "," + peerId + "," + reqIp + "," + peerIp + "," + peerPort + "," + seeder + "," + userAgent;
    }
}
