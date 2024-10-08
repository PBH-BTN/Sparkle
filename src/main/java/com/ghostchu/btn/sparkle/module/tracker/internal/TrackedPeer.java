package com.ghostchu.btn.sparkle.module.tracker.internal;

import com.ghostchu.btn.sparkle.util.ipdb.IPGeoData;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tracker_peers",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"peerId", "torrentInfoHash"})},
        indexes = {@Index(columnList = "peerId"), @Index(columnList = "peerIp"), @Index(columnList = "torrentInfoHash")}
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class TrackedPeer {
    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "peerId", column = @Column(name = "peer_id")),
            @AttributeOverride(name = "torrentInfoHash", column = @Column(name = "torrent_info_hash"))
    })
    private TrackedPeerPK pk;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private InetAddress reqIp;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private String peerIdHumanReadable;
    @Column(nullable = false)
    private InetAddress peerIp;
    @Column(nullable = false)
    private Integer peerPort;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private Long uploadedOffset;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private Long downloadedOffset;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private Long left;
    @Column(nullable = false)
    private PeerEvent lastEvent;
    @Column(nullable = false)
    @Basic(fetch = FetchType.LAZY)
    private String userAgent;
    @Column(nullable = false)
    private OffsetDateTime lastTimeSeen;
    @Column(columnDefinition = "jsonb")
    @Type(JsonType.class)
    @Basic(fetch = FetchType.LAZY)
    private IPGeoData peerGeoIP;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String numWant;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String supportCrypto;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String key;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String cryptoPort;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Integer azudp;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Boolean hide;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Integer azhttp;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Long corrupt;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Long redundant;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String trackerId;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Boolean azq;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String azver;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private Integer azup;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String azas;
    @Column
    @Basic(fetch = FetchType.LAZY)
    private String aznp;
}
