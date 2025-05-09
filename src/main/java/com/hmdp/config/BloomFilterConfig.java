package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 布隆过滤器配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bloom.filter")
public class BloomFilterConfig {
    
    /**
     * 是否启用布隆过滤器
     */
    private boolean enabled = true;
    
    /**
     * 默认使用的布隆过滤器策略
     * 可选值：redisson, local
     */
    private String defaultStrategy = "redisson";
    
    /**
     * 默认预估元素数量
     */
    private long defaultExpectedInsertions = 10000;
    
    /**
     * 默认误判率
     */
    private double defaultFalseProbability = 0.01;
    
    /**
     * 布隆过滤器配置映射
     * key: 布隆过滤器名称
     */
    private Map<String, BloomFilterDetail> filters = new HashMap<>();
    
    /**
     * 获取指定名称的布隆过滤器配置
     * 如果不存在，则返回默认配置
     */
    public BloomFilterDetail getFilterConfig(String name) {
        return filters.getOrDefault(name, createDefaultConfig(name));
    }
    
    /**
     * 创建默认配置
     */
    private BloomFilterDetail createDefaultConfig(String name) {
        BloomFilterDetail detail = new BloomFilterDetail();
        detail.setName(name);
        detail.setStrategy(defaultStrategy);
        detail.setExpectedInsertions(defaultExpectedInsertions);
        detail.setFalseProbability(defaultFalseProbability);
        return detail;
    }
    
    /**
     * 布隆过滤器详细配置
     */
    @Data
    public static class BloomFilterDetail {
        /**
         * 布隆过滤器名称
         */
        private String name;
        
        /**
         * 使用的策略
         */
        private String strategy;
        
        /**
         * 预估元素数量
         */
        private long expectedInsertions;
        
        /**
         * 误判率
         */
        private double falseProbability;
    }
} 