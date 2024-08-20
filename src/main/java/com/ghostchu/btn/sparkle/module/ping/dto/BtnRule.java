package com.ghostchu.btn.sparkle.module.ping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghostchu.btn.sparkle.module.rule.RuleDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnRule implements Serializable {
    @JsonProperty("version")
    private String version;
    @JsonProperty("peer_id")
    private Map<String, List<String>> peerIdRules;
    @JsonProperty("peer_id_exclude")
    private Map<String, List<String>> excludePeerIdRules;
    @JsonProperty("client_name")
    private Map<String, List<String>> clientNameRules;
    @JsonProperty("client_name_exclude")
    private Map<String, List<String>> excludeClientNameRules;
    @JsonProperty("ip")
    private Map<String, List<String>> ipRules;
    @JsonProperty("port")
    private Map<String, List<Integer>> portRules;


    public BtnRule(List<RuleDto> list) {
        this.ipRules = new HashMap<>();
        this.excludePeerIdRules = new HashMap<>();
        this.excludeClientNameRules = new HashMap<>();
        this.portRules = new HashMap<>();
        this.peerIdRules = new HashMap<>();
        this.clientNameRules = new HashMap<>();
        for (RuleDto ruleEntityDto : list) {
            switch (ruleEntityDto.getType()) {
                case "ip" -> {
                    List<String> cat = ipRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(ruleEntityDto.getContent());
                    ipRules.put(ruleEntityDto.getCategory(), cat);
                }
                case "port" -> {
                    List<Integer> cat = portRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(Integer.parseInt(ruleEntityDto.getContent()));
                    portRules.put(ruleEntityDto.getCategory(), cat);
                }
                case "client_name" -> {
                    List<String> cat = clientNameRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(ruleEntityDto.getContent());
                    clientNameRules.put(ruleEntityDto.getCategory(), cat);
                }
                case "peer_id" -> {
                    List<String> cat = peerIdRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(ruleEntityDto.getContent());
                    peerIdRules.put(ruleEntityDto.getCategory(), cat);
                }
                case "client_name_exclude" -> {
                    List<String> cat = excludeClientNameRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(ruleEntityDto.getContent());
                    clientNameRules.put(ruleEntityDto.getCategory(), cat);
                }
                case "exclude_peer_id" -> {
                    List<String> cat = excludePeerIdRules.getOrDefault(ruleEntityDto.getCategory(), new ArrayList<>());
                    cat.add(ruleEntityDto.getContent());
                    excludePeerIdRules.put(ruleEntityDto.getCategory(), cat);
                }
            }
        }
    }
}
