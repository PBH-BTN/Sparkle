package com.ghostchu.btn.sparkle.module.tracker.internal;

import lombok.*;

import java.util.Objects;

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
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackedPeer that = (TrackedPeer) o;
        return Objects.equals(peerId, that.peerId) && Objects.equals(peerIp, that.peerIp) && Objects.equals(peerPort, that.peerPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, peerIp, peerPort);
    }

    public String toKey() {
        return peerId + "," + reqIp + "," + peerIp + "," + peerPort + "," + seeder + "," + userAgent;
    }
}
