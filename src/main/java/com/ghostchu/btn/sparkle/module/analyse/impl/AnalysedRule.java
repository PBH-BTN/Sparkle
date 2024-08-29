package com.ghostchu.btn.sparkle.module.analyse.impl;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "analyse_rules",
        indexes = {@Index(columnList = "module"),@Index(columnList = "ip")}
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class AnalysedRule {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String ip;
    @Column(nullable = false)
    private String module;
    @Column(nullable = false)
    private String comment;
}
