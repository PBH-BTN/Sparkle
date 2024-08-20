package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeer implements Serializable {

    @NotNull
    @NotEmpty
    @JsonProperty("ip_address")
    private String ipAddress;

    @NotNull
    @PositiveOrZero
    @JsonProperty("peer_port")
    private int peerPort;

    @NotNull
    @JsonProperty("peer_id")
    private String peerId;

    @NotNull
    @JsonProperty("client_name")
    private String clientName;

    @NotNull
    @NotEmpty
    @Size(min = 64, max = 64) // 固定长度
    @JsonProperty("torrent_identifier")
    private String torrentIdentifier;

    @NotNull
    @PositiveOrZero
    @JsonProperty("torrent_size")
    private long torrentSize;

    @NotNull
    @JsonProperty("downloaded")
    private long downloaded;

    @NotNull
    @JsonProperty("rt_download_speed")
    private long rtDownloadSpeed;

    @NotNull
    @JsonProperty("uploaded")
    private long uploaded;

    @NotNull
    @JsonProperty("rt_upload_speed")
    private long rtUploadSpeed;

    @NotNull
    @JsonProperty("peer_progress")
    private double peerProgress;

    @NotNull
    @JsonProperty("downloader_progress")
    private double downloaderProgress;

    @NotNull
    @JsonProperty("peer_flag")
    private String peerFlag;
}
