package com.ghostchu.btn.sparkle.module.torrent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TorrentDto {
    private Long id;
    private String identifier;
    private Long size;
}
