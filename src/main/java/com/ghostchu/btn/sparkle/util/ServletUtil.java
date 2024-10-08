package com.ghostchu.btn.sparkle.util;

import com.ghostchu.btn.sparkle.module.ping.ClientAuthenticationCredential;
import jakarta.servlet.http.HttpServletRequest;

public class ServletUtil {

    public static String getIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Rewrite-Peer-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("CF-Connecting-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static ClientAuthenticationCredential getAuthenticationCredential(HttpServletRequest request) {
        ClientAuthenticationCredential cred = readModernFromAuthentication(request);
        if (cred.isValid()) {
            return cred;
        }
        cred = readOldModernFromAuthentication(request); // 显然，BUG 变成了特性
        if (cred.isValid()) {
            return cred;
        }
        cred = readModernFromHeader(request);
        if (cred.isValid()) {
            return cred;
        }
        cred = readLegacy(request);
        return cred;
    }


    private static ClientAuthenticationCredential readOldModernFromAuthentication(HttpServletRequest request) {
        String header = request.getHeader("Authentication");
        if (header == null) {
            return new ClientAuthenticationCredential(null, null);
        }
        header = header.substring(7);
        String[] parser = header.split("@", 2);
        if (parser.length == 2) {
            return new ClientAuthenticationCredential(parser[0], parser[1]);
        }
        return new ClientAuthenticationCredential(null, null);
    }

    private static ClientAuthenticationCredential readModernFromAuthentication(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return new ClientAuthenticationCredential(null, null);
        }
        header = header.substring(7);
        String[] parser = header.split("@", 2);
        if (parser.length == 2) {
            return new ClientAuthenticationCredential(parser[0], parser[1]);
        }
        return new ClientAuthenticationCredential(null, null);
    }

    private static ClientAuthenticationCredential readModernFromHeader(HttpServletRequest request) {
        String appId = request.getHeader("X-BTN-AppID");
        String appSecret = request.getHeader("X-BTN-AppSecret");
        return new ClientAuthenticationCredential(appId, appSecret);
    }

    private static ClientAuthenticationCredential readLegacy(HttpServletRequest request) {
        String appId = request.getHeader("BTN-AppID");
        String appSecret = request.getHeader("BTN-AppSecret");
        return new ClientAuthenticationCredential(appId, appSecret);
    }
}
