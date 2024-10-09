package com.ghostchu.btn.sparkle.module.tracker.internal;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TrackedPeerPK implements Serializable {
    private String peerId;
    private String torrentInfoHash;
}
