package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnPeerPing {
    @JsonProperty("populate_time")
    @NotNull
    private long populateTime;
    @JsonProperty("peers")
    private List<BtnPeer> peers;

}
