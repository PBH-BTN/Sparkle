package com.ghostchu.btn.sparkle.module.peerhistory.internal;

import com.ghostchu.btn.sparkle.module.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.module.userapp.internal.UserApplication;
import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Entity
@Table(name = "peer_history",
        indexes = {@Index(columnList = "lastTimeSeen DESC, torrent, peerIp, userApplication, toPeerTraffic")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class PeerHistory {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private OffsetDateTime insertTime;
    @Column(nullable = false)
    private OffsetDateTime populateTime;
    @JoinColumn(name = "userApplication")
    @ManyToOne(fetch = FetchType.LAZY)
    private UserApplication userApplication;
    @Column(nullable = false)
    private String submitId;
    @Column(nullable = false)
    private InetAddress peerIp;
    @Column
    private String peerId;
    @Column
    private String peerClientName;
    @JoinColumn(name = "torrent")
    @ManyToOne(fetch = FetchType.LAZY)
    private Torrent torrent;
    @Column(nullable = false)
    private Long fromPeerTraffic;
    @Column(nullable = false)
    private Long fromPeerTrafficOffset;
    @Column(nullable = false)
    private Long toPeerTraffic;
    @Column(nullable = false)
    private Long toPeerTrafficOffset;
    @Column(nullable = false)
    private OffsetDateTime firstTimeSeen;
    @Column(nullable = false)
    private OffsetDateTime lastTimeSeen;
    private String flags;
    @Column(nullable = false)
    private InetAddress submitterIp;
    @Version
    @Column(nullable = false)
    private long version;
//    @Column( columnDefinition = "jsonb")
//    @Type(JsonType.class)
//    private IPGeoData geoIP;
}
