package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TrackedPeer {
    private String peerId;
    private String reqIp;
    private String peerIp;
    private Integer peerPort;
    private Long uploadedOffset;
    private Long downloadedOffset;
    private Long left;
    private PeerEvent lastEvent;
    private String userAgent;
    private OffsetDateTime lastTimeSeen;
    private IPGeoData peerGeoIP;

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
}
