package com.ghostchu.btn.sparkle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.module.tracker.internal.TrackedPeer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisSocketConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class SpringRedisConfig {
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(@Value("${sparkle.redis.unixsocket}") String redisSocket, LettuceConnectionFactory lettuceConnectionFactory) {
        if (redisSocket == null || redisSocket.isEmpty()) {
            return lettuceConnectionFactory;
        }
        return new LettuceConnectionFactory(new RedisSocketConfiguration(redisSocket));
    }
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory redisConnectionFactory, ObjectMapper mapper) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        return redisTemplate;
    }

    @Bean("longRedisTemplate")
    public RedisTemplate<String, Long> longRedisTemplate(LettuceConnectionFactory redisConnectionFactory, ObjectMapper mapper) {
        RedisTemplate<String, Long> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        return redisTemplate;
    }

    @Bean("redisTemplateTrackedPeer")
    public RedisTemplate<String, TrackedPeer> redisTemplateTrackedPeer(LettuceConnectionFactory redisConnectionFactory, ObjectMapper mapper) {
        RedisTemplate<String, TrackedPeer> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, TrackedPeer.class));
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, TrackedPeer.class));
        return redisTemplate;
    }

}
