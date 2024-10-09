package com.ghostchu.btn.sparkle.module.tracker.internal;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class TrackedPeerId {
    @Column(nullable = false)
    private String peerId;
    @Column(nullable = false)
    private String torrentInfoHash;
}
