package com.ghostchu.btn.sparkle.module.banhistory.internal;

import java.net.InetAddress;

public interface UntrustIpAddressProjection {
    InetAddress getPeerIp();

    Integer getCount();
}
