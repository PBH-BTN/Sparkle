package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "clientdiscovery")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ClientDiscovery {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long hash;
    @Column(nullable = false)
    private String clientName;
    @Column(nullable = false)
    private String peerId;
    @Column(nullable = false)
    private OffsetDateTime foundAt;
}
