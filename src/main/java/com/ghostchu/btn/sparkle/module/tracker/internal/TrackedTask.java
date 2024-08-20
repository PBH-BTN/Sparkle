package com.ghostchu.btn.sparkle.module.tracker.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "tracker_tasks",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"torrentInfoHash"})},
        indexes = {@Index(columnList = "torrentInfoHash")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TrackedTask {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String torrentInfoHash;
    @Column(nullable = false)
    private Timestamp firstTimeSeen;
    @Column(nullable = false)
    private Timestamp lastTimeSeen;
    @Column(nullable = false)
    private Long leechCount;
    @Column(nullable = false)
    private Long downloadedCount;
}
