package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeerHistoryPing {
    @JsonProperty("populate_time")
    private long populateTime;
    @JsonProperty("peers")
    private List<BtnPeerHistory> peers;

}
