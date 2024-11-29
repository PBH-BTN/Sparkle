package com.ghostchu.btn.sparkle.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class InetAddressDeserializer extends JsonDeserializer<InetAddress> {
    @Override
    public InetAddress deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        String ip = parser.getText();
        try {
            // 使用 createUnresolved 创建未解析的 InetAddress
            return InetSocketAddress.createUnresolved(ip, 0).getAddress();
        } catch (IllegalArgumentException e) {
            return InetSocketAddress.createUnresolved("127.123.123.123", 0).getAddress();
        }
    }
}
