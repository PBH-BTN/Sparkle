package com.ghostchu.btn.sparkle.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
@EnableCaching
public class SparkleRedisCacheConfig {
    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(1));

        return new SparkleRedisCacheManager(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory), defaultCacheConfig);
    }
}
