package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeerHistory {
    @JsonProperty("ip_address")
    private String ipAddress;
    @JsonProperty("peer_id")
    private String peerId;
    @JsonProperty("client_name")
    private String clientName;
    @JsonProperty("torrent_identifier")
    private String torrentIdentifier;
    @JsonProperty("torrent_size")
    private long torrentSize;
    @JsonProperty("downloaded")
    private long downloaded;
    @JsonProperty("downloaded_offset")
    private long downloadedOffset;
    @JsonProperty("uploaded")
    private long uploaded;
    @JsonProperty("uploaded_offset")
    private long uploadedOffset;
    @JsonProperty("first_time_seen")
    private Timestamp firstTimeSeen;
    @JsonProperty("last_time_seen")
    private Timestamp lastTimeSeen;
    @JsonProperty("peer_flag")
    private String peerFlag;

}
