package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnBan {

    @NotNull
    @JsonProperty("btn_ban")
    private boolean btnBan;

    @NotNull
    @JsonProperty("ban_unique_id")
    private String banUniqueId;

    @NotNull
    @JsonProperty("module")
    private String module;

    @NotNull
    @JsonProperty("rule")
    private String rule;

    @NotNull
    @JsonProperty("peer")
    private BtnPeer peer;
}
