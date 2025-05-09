package com.hmdp.utils.bloom;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import orestes.bloomfilter.BloomFilter;
import orestes.bloomfilter.FilterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于Caffeine和Orestes.Bloom的本地内存布隆过滤器策略实现
 * 适用于String类型的布隆过滤器
 */
@Slf4j
@Component
public class LocalBloomFilterStrategy implements BloomFilterStrategy<String> {
    
    private BloomFilter<String> bloomFilter;
    private String name;
    private final AtomicLong count = new AtomicLong(0);
    
    // 使用Caffeine缓存提高命中率和性能
    private Cache<String, Boolean> resultCache;
    
    /**
     * 获取布隆过滤器实例
     * @param name 布隆过滤器名称
     * @return 当前策略实例
     */
    public LocalBloomFilterStrategy withName(String name) {
        this.name = name;
        // 初始化结果缓存，提高性能
        this.resultCache = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10000)
                .build();
        return this;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void init(long expectedInsertions, double falseProbability) {
        // 使用Orestes.Bloom库创建布隆过滤器
        this.bloomFilter = new FilterBuilder(expectedInsertions, falseProbability)
                .name(name)
                .buildBloomFilter();
        
        log.info("本地布隆过滤器[{}]初始化完成，预计元素数量：{}，误判率：{}", 
                name, expectedInsertions, falseProbability);
    }
    
    @Override
    public boolean add(String value) {
        if (value == null) {
            return false;
        }
        
        boolean result = bloomFilter.add(value);
        if (result) {
            count.incrementAndGet();
            // 更新缓存
            resultCache.put(value, true);
        }
        return result;
    }
    
    @Override
    public void addAll(Iterable<String> values) {
        if (values == null) {
            return;
        }
        
        for (String value : values) {
            add(value);
        }
    }
    
    @Override
    public boolean contains(String value) {
        if (value == null) {
            return false;
        }
        
        // 先查缓存，提高性能
        Boolean cached = resultCache.getIfPresent(value);
        if (cached != null && cached) {
            return true;
        }
        
        // 缓存未命中，查询布隆过滤器
        boolean result = bloomFilter.contains(value);
        
        // 如果确认存在，缓存结果
        if (result) {
            resultCache.put(value, true);
        }
        
        return result;
    }
    
    @Override
    public long count() {
        return count.get();
    }
    
    @Override
    public void clear() {
        // 创建新的布隆过滤器
        this.bloomFilter = new FilterBuilder(1000, 0.01)
                .name(name)
                .buildBloomFilter();
        
        // 清空计数器和缓存
        count.set(0);
        resultCache.invalidateAll();
        
        log.info("本地布隆过滤器[{}]已清空", name);
    }
} 