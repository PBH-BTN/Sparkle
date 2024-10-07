package com.ghostchu.btn.sparkle.util;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.net.InetAddress;

public class IPUtil {

    public static IPAddress toIPAddress(String ip) {
        var address = new IPAddressString(ip).getAddress();
        if (address == null) {
            System.err.println("Unable convert " + ip + " to IPAddressString");
            return new IPAddressString("127.0.0.128").getAddress();
        }
        return address;
    }

    public static InetAddress toInet(String ip) {
        var address = new IPAddressString(ip).getAddress();
        if (address == null) {
            System.err.println("Unable convert " + ip + " to IPAddressString");
            return new IPAddressString("127.0.0.128").getAddress().toInetAddress();
        }
        return address.toInetAddress();
    }

    public static String toString(InetAddress inet) { // 压缩一下 :0:0:0
        var address = new IPAddressString(inet.getHostAddress()).getAddress();
        if (address == null) {
            System.err.println("Unable convert " + inet + " to IPAddressString");
            return "127.0.0.128";
        }
        return address.toString();
    }
}
