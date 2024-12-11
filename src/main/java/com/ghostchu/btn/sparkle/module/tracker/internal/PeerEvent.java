package com.ghostchu.btn.sparkle.module.tracker.internal;

import java.io.Serializable;

public enum PeerEvent implements Serializable {
    STARTED,
    COMPLETED,
    STOPPED,
    EMPTY;

    public static PeerEvent fromString(String event) {
        return switch (event) {
            case "started" -> STARTED;
            case "completed" -> COMPLETED;
            case "stopped" -> STOPPED;
            default -> EMPTY;
        };
    }
}
