package com.ghostchu.btn.sparkle.module.torrent.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "torrent",
        uniqueConstraints = {@UniqueConstraint(columnNames = "identifier, size")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Torrent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String identifier;
    @Column(nullable = false)
    private Long size;
}
