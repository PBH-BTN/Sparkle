package com.ghostchu.btn.sparkle.module.ping.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeerHistory {
    @SerializedName("ip_address")
    private String ipAddress;
    @SerializedName("peer_id")
    private String peerId;
    @SerializedName("client_name")
    private String clientName;
    @SerializedName("torrent_identifier")
    private String torrentIdentifier;
    @SerializedName("torrent_size")
    private long torrentSize;
    @SerializedName("downloaded")
    private long downloaded;
    @SerializedName("downloaded_offset")
    private long downloadedOffset;
    @SerializedName("uploaded")
    private long uploaded;
    @SerializedName("uploaded_offset")
    private long uploadedOffset;
    @SerializedName("first_time_seen")
    private Timestamp firstTimeSeen;
    @SerializedName("last_time_seen")
    private Timestamp lastTimeSeen;
    @SerializedName("peer_flag")
    private String peerFlag;

}
