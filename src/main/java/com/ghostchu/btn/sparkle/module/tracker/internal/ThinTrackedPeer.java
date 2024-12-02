package com.ghostchu.btn.sparkle.module.tracker.internal;

import java.net.InetAddress;

public interface ThinTrackedPeer {
    InetAddress getPeerIp();

    int getPeerPort();

    long getLeft();
}
