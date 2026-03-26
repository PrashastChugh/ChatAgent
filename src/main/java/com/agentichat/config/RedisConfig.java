package com.agentichat.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration.
 *
 * WHY CONFIGURE MANUALLY?
 * Spring Boot auto-configures basic Redis, but we need:
 * 1. JSON serialization (by default Spring uses Java binary serialization — unreadable)
 * 2. Custom TTL (time-to-live) per cache type
 * 3. A typed RedisTemplate for working with Redis directly in code
 */
@Configuration
@EnableCaching  // Activates Spring's @Cacheable, @CacheEvict, @CachePut annotations
public class RedisConfig {

    /**
     * ObjectMapper configured for Redis serialization.
     * We need a SEPARATE mapper here (not the app-wide one) because Redis needs
     * type information embedded in the JSON so it can deserialize back to the right class.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Handle Java 8+ date/time types (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Embed class type info in JSON: {"@class":"com.agentichat.model.dto.ChatMessageDto", ...}
        // Required so Redis knows what Java class to deserialize to
        mapper.activateDefaultTyping(
            mapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * RedisTemplate: the main way to interact with Redis programmatically.
     * Like JdbcTemplate for DB, but for Redis.
     *
     * We define key serializer as String (readable keys like "conversation:abc123")
     * and value serializer as JSON (readable, debuggable values).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // Keys are Strings: "memory:conv-uuid-123"
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Values are JSON objects
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * RedisCacheManager: powers @Cacheable annotations.
     * Defines default TTL and per-cache TTL overrides.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // Default config: all caches expire after 10 minutes
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues(); // Don't cache null results

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            // conversation-memory cache: longer TTL since users expect context to persist
            .withCacheConfiguration("conversation-memory",
                defaultConfig.entryTtl(Duration.ofHours(1)))
            // tool-results cache: short TTL since external data changes frequently
            .withCacheConfiguration("tool-results",
                defaultConfig.entryTtl(Duration.ofMinutes(5)))
            .build();
    }
}