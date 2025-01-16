package com.ghostchu.btn.sparkle.module.torrent.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "torrent",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"identifier","size"})},
        indexes = {@Index(columnList = "identifier"), @Index(columnList = "id, size")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Torrent implements Serializable {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String identifier;
    @Column(nullable = false)
    private Long size;
    @Column()
    private Boolean privateTorrent;
}
