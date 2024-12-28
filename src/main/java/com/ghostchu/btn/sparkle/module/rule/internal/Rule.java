package com.ghostchu.btn.sparkle.module.rule.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rule",
        indexes = {@Index(columnList = "category"), @Index(columnList = "type"), @Index(columnList = "expiredAt DESC")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Rule {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private OffsetDateTime expiredAt;
}
