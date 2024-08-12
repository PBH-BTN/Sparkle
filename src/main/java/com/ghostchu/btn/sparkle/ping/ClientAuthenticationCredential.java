package com.ghostchu.btn.sparkle.ping;

public record ClientAuthenticationCredential(
        String appId,
        String appSecret
) {
    public boolean isValid(){
        return appId != null && appSecret != null;
    }
    public void verifyOrThrow(){
        if(!isValid()){
            throw new IllegalArgumentException("请求未鉴权，客户端实现必须进行登录鉴权：https://github.com/PBH-BTN/BTN-Spec");
        }
    }
}
