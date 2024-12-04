package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TrackedPeer {
    private String peerId;
    private InetAddress reqIp;
    private InetAddress peerIp;
    private Integer peerPort;
    private Long uploadedOffset;
    private Long downloadedOffset;
    private Long left;
    private PeerEvent lastEvent;
    private String userAgent;
    private OffsetDateTime lastTimeSeen;
    private IPGeoData peerGeoIP;
}
