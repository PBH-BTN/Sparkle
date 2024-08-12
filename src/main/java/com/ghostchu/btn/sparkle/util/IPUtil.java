package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddressString;

import java.net.InetAddress;

public class IPUtil {
    public static InetAddress toInet(String ip) {
        return new IPAddressString(ip).getAddress().toInetAddress();
    }
}
