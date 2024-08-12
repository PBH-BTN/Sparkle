package com.ghostchu.btn.sparkle.snapshot.internal;

import com.ghostchu.btn.sparkle.torrent.internal.Torrent;
import com.ghostchu.btn.sparkle.userapp.internal.UserApplication;
import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.sql.Timestamp;

@Entity
@Table(name = "snapshot",
        indexes = {@Index(columnList = "insertTime")
                , @Index(columnList = "userApplication")
                , @Index(columnList = "peerId")
                , @Index(columnList = "peerClientName")
                , @Index(columnList = "torrent")
                , @Index(columnList = "peerIp")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Snapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private Timestamp insertTime;
    @Column(nullable = false)
    private Timestamp populateTime;
    @JoinColumn(name = "userApplication")
    @ManyToOne(fetch = FetchType.LAZY)
    private UserApplication userApplication;
    @Column(nullable = false)
    private String submitId;
    @Column(nullable = false)
    private InetAddress peerIp;
    @Column(nullable = false)
    private Integer peerPort;
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
    private Long fromPeerTrafficSpeed;
    @Column(nullable = false)
    private Long toPeerTraffic;
    @Column(nullable = false)
    private Long toPeerTrafficSpeed;
    @Column(nullable = false)
    private Double peerProgress;
    @Column(nullable = false)
    private Double downloaderProgress;
    @Column(nullable = false)
    private String flags;
    @Column(nullable = false)
    private InetAddress submitterIp;
}
