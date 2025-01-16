package com.ghostchu.btn.sparkle.module.torrent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TorrentDto implements Serializable {
    private Long id;
    private String identifier;
    private Long size;
    private Boolean privateTorrent;
}
