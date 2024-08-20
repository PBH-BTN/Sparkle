package com.ghostchu.btn.sparkle.module.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleDto implements Serializable {
    private Long id;
    private String category;
    private String content;
    private String type;
    private Long createdAt;
    private Long expiredAt;
}
