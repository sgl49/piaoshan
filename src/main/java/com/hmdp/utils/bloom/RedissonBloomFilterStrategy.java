package com.hmdp.utils.bloom;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 基于Redisson的布隆过滤器策略实现
 */
@Slf4j
@Component
public class RedissonBloomFilterStrategy<T> implements BloomFilterStrategy<T> {
    
    private final RedissonClient redissonClient;
    private RBloomFilter<T> bloomFilter;
    private String name;
    
    public RedissonBloomFilterStrategy(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
    
    /**
     * 获取布隆过滤器实例
     * @param name 布隆过滤器名称
     * @return 当前策略实例
     */
    public RedissonBloomFilterStrategy<T> withName(String name) {
        this.name = name;
        this.bloomFilter = redissonClient.getBloomFilter(name);
        return this;
    }
    
    @Override
    public String getName() {
        return this.name;
    }
    
    @Override
    public void init(long expectedInsertions, double falseProbability) {
        if (bloomFilter == null) {
            throw new IllegalStateException("布隆过滤器尚未命名，请先调用withName方法");
        }
        
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        log.info("Redisson布隆过滤器[{}]初始化完成，预计元素数量：{}，误判率：{}", 
                name, expectedInsertions, falseProbability);
    }
    
    @Override
    public boolean add(T value) {
        return bloomFilter.add(value);
    }
    
    @Override
    public void addAll(Iterable<T> values) {
        values.forEach(bloomFilter::add);
    }
    
    @Override
    public boolean contains(T value) {
        return bloomFilter.contains(value);
    }
    
    @Override
    public long count() {
        return bloomFilter.count();
    }
    
    @Override
    public void clear() {
        bloomFilter.delete();
        log.info("Redisson布隆过滤器[{}]已清空", name);
    }
} 