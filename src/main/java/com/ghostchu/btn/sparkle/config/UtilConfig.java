package com.ghostchu.btn.sparkle.config;

import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilConfig {
    @Bean
    public UnirestInstance unirest() {
        UnirestInstance instance = Unirest.spawnInstance();
        instance.config()
                .addDefaultHeader("User-Agent", "Sparkle(BTN-Server)/1.0")
                .enableCookieManagement(true);
        return instance;
    }
}
