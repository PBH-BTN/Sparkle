package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class IPUtil {
    public static final String INVALID_FALLBACK_ADDRESS = "127.0.0.128";
    public static IPAddress toIPAddress(String ip) {
        var address = new IPAddressString(ip).getAddress();
        if (address == null) {
            //log.error("Unable convert {} to IPAddressString (toIPAddress)", ip);
            return new IPAddressString(INVALID_FALLBACK_ADDRESS).getAddress();
        }
        return address;
    }

    public static IPAddress toIPAddress(byte[] bytes) throws UnknownHostException {
        var address = InetAddress.getByAddress(bytes);
        return toIPAddress(address.getHostAddress());
    }

    public static InetAddress toInet(String ip) {
        var address = new IPAddressString(ip).getAddress();
        if (address == null) {
            log.error("Unable convert {} to IPAddressString (toInet)", ip);
            return new IPAddressString(INVALID_FALLBACK_ADDRESS).getAddress().toInetAddress();
        }
        return address.toInetAddress();
    }

    public static String toString(InetAddress inet) { // 压缩一下 :0:0:0
        return inet.getHostAddress();
    }
}
