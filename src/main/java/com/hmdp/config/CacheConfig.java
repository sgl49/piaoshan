package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cache")
public class CacheConfig {
    /**
     * 本地缓存配置
     */
    private LocalCache local = new LocalCache();
    
    /**
     * Redis缓存配置
     */
    private RedisCache redis = new RedisCache();
    
    /**
     * 本地缓存配置
     */
    @Data
    public static class LocalCache {
        /**
         * 初始容量
         */
        private int initialCapacity = 100;
        
        /**
         * 最大容量
         */
        private int maximumSize = 10000;
        
        /**
         * 写入后过期时间（秒）
         */
        private int expireAfterWrite = 300;
    }
    
    /**
     * Redis缓存配置
     */
    @Data
    public static class RedisCache {
        /**
         * 默认过期时间（秒）
         */
        private long defaultTtl = 1800;
        
        /**
         * 空值过期时间（秒）
         */
        private long nullTtl = 60;
    }
} 