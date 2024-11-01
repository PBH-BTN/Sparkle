package com.ghostchu.btn.sparkle.module.ping;

import java.io.Serializable;

public class ClientAuthenticationCredential implements Serializable {
    private String appId;
    private String appSecret;

    public ClientAuthenticationCredential(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public String appId() {
        return appId.trim();
    }

    public String appSecret() {
        return appSecret.trim();
    }

    public boolean isValid() {
        return appId != null && appSecret != null;
    }

    public void verifyOrThrow() {
        if (!isValid()) {
            throw new IllegalArgumentException("请求未鉴权，客户端实现必须进行登录鉴权：https://github.com/PBH-BTN/BTN-Spec");
        }
    }
}
