package com.ghostchu.btn.sparkle.module.banhistory;

import com.ghostchu.btn.sparkle.module.torrent.TorrentDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BanHistoryDto {
    private Long id;
    private String appId;
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
    private Boolean btnBan;
    private String module;
    private String rule;
    private String banUniqueId;
}
