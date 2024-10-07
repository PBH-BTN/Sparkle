package com.ghostchu.btn.sparkle.module.tracker.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tracker_tasks",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"torrentInfoHash"})},
        indexes = {@Index(columnList = "torrentInfoHash")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class TrackedTask {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String torrentInfoHash;
    @Column(nullable = false)
    private OffsetDateTime firstTimeSeen;
    @Column(nullable = false)
    private OffsetDateTime lastTimeSeen;
    @Column(nullable = false)
    private Long leechCount;
    @Column(nullable = false)
    private Long downloadedCount;
}
