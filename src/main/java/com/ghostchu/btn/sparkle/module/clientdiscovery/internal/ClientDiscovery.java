package com.ghostchu.btn.sparkle.module.clientdiscovery.internal;

import com.ghostchu.btn.sparkle.module.user.internal.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.sql.Timestamp;

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
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long hash;
    @Column(nullable = false)
    private String clientName;
    @Column(nullable = false)
    private String peerId;
    @Column(nullable = false)
    private Timestamp foundAt;
    @JoinColumn(name = "foundBy")
    @ManyToOne(fetch = FetchType.LAZY)
    private User foundBy;
    @Column(nullable = false)
    private Timestamp lastSeenAt;
    @JoinColumn(name = "lastSeenBy")
    @ManyToOne(fetch = FetchType.LAZY)
    private User lastSeenBy;
}
