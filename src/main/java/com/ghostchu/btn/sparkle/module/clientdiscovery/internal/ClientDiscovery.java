package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

@Entity
@Table(name = "clientdiscovery")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@DynamicUpdate
public class ClientDiscovery {
    @Id
    @Column(nullable = false, unique = true)
    private Long hash;
    @Column(nullable = false)
    private String clientName;
    @Column(nullable = false)
    private String peerId;
    @Column(nullable = false)
    private OffsetDateTime foundAt;
    @Column(nullable = false)
    private OffsetDateTime lastSeenAt;
}
