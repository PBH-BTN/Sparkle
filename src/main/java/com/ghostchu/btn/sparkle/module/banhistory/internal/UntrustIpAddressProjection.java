package com.ghostchu.btn.sparkle.module.banhistory.internal;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.net.InetAddress;

@AllArgsConstructor
@Data
public class UntrustIpAddressProjection {
    private InetAddress peerIp;
    private Integer count;
}
