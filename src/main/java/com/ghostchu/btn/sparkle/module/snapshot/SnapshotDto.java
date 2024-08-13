package com.ghostchu.btn.sparkle.module.snapshot;

import com.ghostchu.btn.sparkle.module.torrent.TorrentDto;
import com.ghostchu.btn.sparkle.module.userapp.UserApplicationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SnapshotDto {
    private Long id;
    private UserApplicationDto userApplication;
    private String submitId;
    private String peerIp;
    private Integer peerPort;
    private String peerId;
    private String peerClientName;
    private TorrentDto torrent;
    private Long fromPeerTraffic;
    private Long fromPeerTrafficSpeed;
    private Long toPeerTraffic;
    private Long toPeerTrafficSpeed;
    private Double peerProgress;
    private Double downloaderProgress;
    private String flags;
    private String submitterIp;
}
