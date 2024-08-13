package com.ghostchu.btn.sparkle.module.rule.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "rule",
        indexes = {@Index(columnList = "category"), @Index(columnList = "type"), @Index(columnList = "expiredAt DESC")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Rule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String content;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private Timestamp createdAt;
    @Column(nullable = false)
    private Timestamp expiredAt;
}
