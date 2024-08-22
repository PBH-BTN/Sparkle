package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddressString;

import java.net.InetAddress;

public class IPUtil {
    public static InetAddress toInet(String ip) {
        return new IPAddressString(ip).getAddress().toInetAddress();
    }

    public static String toString(InetAddress inet) { // 压缩一下 :0:0:0
        return new IPAddressString(inet.getHostAddress()).getAddress().toString();
    }
}
