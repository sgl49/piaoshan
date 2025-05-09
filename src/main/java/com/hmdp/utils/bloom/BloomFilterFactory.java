package com.hmdp.utils.bloom;

import com.hmdp.config.BloomFilterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 布隆过滤器工厂
 * 负责创建和管理不同实现的布隆过滤器
 */
@Slf4j
@Component
public class BloomFilterFactory {
    
    private final BloomFilterConfig config;
    private final RedissonBloomFilterStrategy<String> redissonStrategy;
    private final LocalBloomFilterStrategy localStrategy;
    
    // 策略映射，用于保存策略名和实现类的映射关系
    private final Map<String, BloomFilterStrategy<String>> strategies = new HashMap<>();
    
    // 布隆过滤器实例缓存
    private final Map<String, BloomFilterStrategy<String>> filterInstances = new ConcurrentHashMap<>();
    
    public BloomFilterFactory(
            BloomFilterConfig config,
            RedissonBloomFilterStrategy<String> redissonStrategy,
            LocalBloomFilterStrategy localStrategy) {
        this.config = config;
        this.redissonStrategy = redissonStrategy;
        this.localStrategy = localStrategy;
    }
    
    @PostConstruct
    public void init() {
        // 注册所有可用的布隆过滤器策略
        strategies.put("redisson", redissonStrategy);
        strategies.put("local", localStrategy);
        
        log.info("布隆过滤器工厂初始化完成，可用策略: {}", strategies.keySet());
    }
    
    /**
     * 获取布隆过滤器实例
     * 如果布隆过滤器不存在，则根据配置创建
     * @param name 布隆过滤器名称
     * @return 布隆过滤器实例
     */
    public BloomFilterStrategy<String> getFilter(String name) {
        return filterInstances.computeIfAbsent(name, this::createFilter);
    }
    
    /**
     * 根据名称创建布隆过滤器
     * @param name 布隆过滤器名称
     * @return 布隆过滤器实例
     */
    private BloomFilterStrategy<String> createFilter(String name) {
        BloomFilterConfig.BloomFilterDetail filterConfig = config.getFilterConfig(name);
        
        // 获取策略实现
        String strategyName = filterConfig.getStrategy();
        BloomFilterStrategy<String> strategy = strategies.get(strategyName);
        
        if (strategy == null) {
            log.warn("未找到名为 {} 的布隆过滤器策略，使用默认策略: {}", strategyName, config.getDefaultStrategy());
            strategy = strategies.get(config.getDefaultStrategy());
        }
        
        // 创建具体的布隆过滤器实例
        BloomFilterStrategy<String> filter;
        if (strategy instanceof RedissonBloomFilterStrategy) {
            filter = ((RedissonBloomFilterStrategy<String>) strategy).withName("bloom:" + name);
        } else if (strategy instanceof LocalBloomFilterStrategy) {
            filter = ((LocalBloomFilterStrategy) strategy).withName("bloom:" + name);
        } else {
            throw new IllegalArgumentException("不支持的布隆过滤器策略: " + strategyName);
        }
        
        // 初始化布隆过滤器
        filter.init(
                filterConfig.getExpectedInsertions(),
                filterConfig.getFalseProbability()
        );
        
        log.info("创建布隆过滤器: name={}, strategy={}, expectedInsertions={}, falseProbability={}", 
                name, strategyName, filterConfig.getExpectedInsertions(), filterConfig.getFalseProbability());
        
        return filter;
    }
    
    /**
     * 获取所有已创建的布隆过滤器名称
     * @return 布隆过滤器名称集合
     */
    public Map<String, BloomFilterStrategy<String>> getAllFilters() {
        return new HashMap<>(filterInstances);
    }
    
    /**
     * 清除指定的布隆过滤器
     * @param name 布隆过滤器名称
     */
    public void clearFilter(String name) {
        BloomFilterStrategy<String> filter = filterInstances.get(name);
        if (filter != null) {
            filter.clear();
            filterInstances.remove(name);
            log.info("布隆过滤器 {} 已清除", name);
        }
    }
    
    /**
     * 获取可用的策略列表
     * @return 策略名称列表
     */
    public Map<String, BloomFilterStrategy<String>> getStrategies() {
        return new HashMap<>(strategies);
    }
} 