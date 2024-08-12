package com.ghostchu.btn.sparkle.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RuleDto {
    private Long id;
    private String category;
    private String content;
    private String type;
    private Long createdAt;
    private Long expiredAt;
}
