package com.ghostchu.btn.sparkle.module.analyse.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AnalysedRule {
    private Long id;
    private String ip;
    private String module;
    private String comment;
}
