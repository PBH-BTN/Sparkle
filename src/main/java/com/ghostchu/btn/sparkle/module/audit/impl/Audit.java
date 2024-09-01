package com.ghostchu.btn.sparkle.module.audit.impl;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "audit",
        indexes = {@Index(columnList = "action"), @Index(columnList = "ip")}
)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Audit {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private Timestamp timestamp;
    @Column
    private InetAddress ip;
    @Column(nullable = false)
    private String action;
    @Column(nullable = false)
    private Boolean success;
    @Column(nullable = false, columnDefinition = "jsonb")
    @Type(JsonType.class)
    private Map<String, List<String>> headers;
    @Column(nullable = false, columnDefinition = "jsonb")
    @Type(JsonType.class)
    private Map<String, Object> details;

}
