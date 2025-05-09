package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.config.CacheConfig;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.CacheChangeMessage;
import com.hmdp.entity.Shop;
import com.hmdp.utils.bloom.BloomFilterFactory;
import com.hmdp.utils.bloom.BloomFilterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * redis工具
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final CacheConfig cacheConfig;
    
    // 布隆过滤器工厂
    private final BloomFilterFactory bloomFilterFactory;
    
    // 布隆过滤器名称常量
    private static final String DEFAULT_BLOOM_FILTER_NAME = "default";
    private static final String SHOP_BLOOM_FILTER_NAME = "shop";
    
    // 本地缓存 - Caffeine
    private final Cache<String, Object> localCache;
    
    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    
    @Autowired
    public CacheClient(
            StringRedisTemplate stringRedisTemplate, 
            RedissonClient redissonClient,
            RabbitTemplate rabbitTemplate,
            CacheConfig cacheConfig,
            BloomFilterFactory bloomFilterFactory) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.rabbitTemplate = rabbitTemplate;
        this.cacheConfig = cacheConfig;
        this.bloomFilterFactory = bloomFilterFactory;
        
        // 初始化本地缓存 Caffeine，使用配置中的参数
        this.localCache = Caffeine.newBuilder()
                .initialCapacity(cacheConfig.getLocal().getInitialCapacity())
                .maximumSize(cacheConfig.getLocal().getMaximumSize())
                .expireAfterWrite(cacheConfig.getLocal().getExpireAfterWrite(), TimeUnit.SECONDS)
                .recordStats() // 记录统计信息
                .build();
    }
    
    /**
     * 从多级缓存中获取数据（本地缓存 -> Redis -> 数据库）
     */
    public <R, ID> R queryWithMultiLevelCache(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {
        String key = keyPrefix + id;
        
        // 1. 查询本地缓存
        R localResult = (R) localCache.getIfPresent(key);
        if (localResult != null) {
            log.debug("本地缓存命中，key: {}", key);
            return localResult;
        }
        
        // 2. 检查布隆过滤器，如果确定不存在，直接返回null
        BloomFilterStrategy<String> bloomFilter = getBloomFilterForKey(key);
        if (!bloomFilter.contains(key)) {
            log.debug("布隆过滤器拦截，key一定不存在: {}", key);
            return null;
        }
        
        // 3. 查询Redis缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(json)) {
            // Redis命中，更新本地缓存
            R result = JSONUtil.toBean(json, type);
            localCache.put(key, result);
            log.debug("Redis缓存命中，key: {}", key);
            return result;
        }
        
        // 4. 查询数据库
        R r = dbFallback.apply(id);
        if (r != null) {
            // 数据库中存在，写入Redis和本地缓存
            this.set(key, r, time, unit);
            localCache.put(key, r);
            // 添加到布隆过滤器
            bloomFilter.add(key);
            log.debug("从数据库加载并写入多级缓存，key: {}", key);
        }
        return r;
    }

    /**
     * 将任意对象序列化成json存入redis
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
        // 同时更新本地缓存
        localCache.put(key, value);
    }

    /**
     * 将任意对象序列化成json存入redis 并且携带逻辑过期时间
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        //同时更新本地缓存
        localCache.put(key, value);
        
        // 将key添加到布隆过滤器
        addToBloomFilter(key);
    }

    /**
     * 使用布隆过滤器解决缓存穿透
     */
    public <R, ID> R queryWithBloomFilter(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {
        String key = keyPrefix + id;
        
        // 获取适合当前key的布隆过滤器
        BloomFilterStrategy<String> bloomFilter = getBloomFilterForKey(key);
        
        // 判断是否可能存在
        if (!bloomFilter.contains(key)) {
            // 一定不存在
            return null;
        }
        // 从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StringUtils.isNotEmpty(json)) {
            // 存在直接返回
            R result = JSONUtil.toBean(json, type);
            return result;
        }

        // 不存在，查询数据库
        R r = dbFallback.apply(id);
        if (r != null) {
            // 数据库存在，写入redis和布隆过滤器
            this.set(key, r, time, unit);
            bloomFilter.add(key);
        }
        return r;
    }

    /**
     * 逻辑过期解决缓存击穿
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {
        String key = keyPrefix + id;
        
        // 1. 查询本地缓存
        R localResult = (R) localCache.getIfPresent(key);
        if (localResult != null) {
            return localResult;
        }
        
        //1、从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.1、缓存不存在直接返回空
        if (StringUtils.isEmpty(json)) {
            return null;
        }

        //2.2、缓存存在，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = BeanUtil.toBean(data, type);

        //3、判断缓存是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //3.1、缓存未过期，直接返回
            return r;
        }

        //3.2、缓存过期，需要重建缓存
        //4、重建缓存
        //4.1、尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        //4.2、判断锁是否获取成功
        if (isLock) {
            //4.3、获取锁成功，开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //4.4、查询数据库
                    R r1 = dbFallback.apply(id);
                    //4.5、写入缓存
                    setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //4.6、释放锁
                    unLock(lockKey);
                }
            });
        }

        //4.7、返回旧数据
        return r;
    }

    /**
     * 互斥锁解决缓存击穿
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit) {
        String key = keyPrefix + id;
        
        // 1. 先查询本地缓存
        R localResult = (R) localCache.getIfPresent(key);
        if (localResult != null) {
            return localResult;
        }
        
        // 2. 查询Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)) {
            R r = JSONUtil.toBean(json, type);
            // 更新本地缓存
            localCache.put(key, r);
            return r;
        }
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 3. 实现缓存重建
        // 3.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 3.2 判断获取锁是否成功
            if (!isLock) {
                // 3.3 失败，则休眠重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 3.4 成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 3.5 不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 3.6 存在，写入redis
            this.set(key, r, time, unit);
            // 将key添加到布隆过滤器
            addToBloomFilter(key);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }
        // 返回
        return r;
    }
    
    /**
     * 删除缓存
     */
    public void deleteCache(String key) {
        // 删除redis缓存
        stringRedisTemplate.delete(key);
        // 删除本地缓存
        localCache.invalidate(key);
        // 发布缓存变更消息
        publishCacheChangeEvent(key, "delete");
    }
    
    /**
     * 发布缓存变更消息
     */
    public void publishCacheChangeEvent(String key, String operation) {
        // 创建缓存变更消息
        CacheChangeMessage message = new CacheChangeMessage();
        message.setKey(key);
        message.setOperation(operation);
        message.setTimestamp(System.currentTimeMillis());
        // 发送到RabbitMQ
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.CACHE_EXCHANGE, 
                                         "",  // Fanout交换机不需要routingKey
                                         message);
            log.debug("发布缓存变更消息：{}", message);
        } catch (Exception e) {
            log.error("发布缓存变更消息失败", e);
        }
    }
    
    /**
     * 处理本地缓存变更消息
     */
    @RabbitListener(queues = RabbitMQConfig.LOCAL_CACHE_QUEUE)
    public void handleLocalCacheChange(CacheChangeMessage message) {
        if (message == null) {
            return;
        }
        
        String key = message.getKey();
        String operation = message.getOperation();
        
        log.debug("收到缓存变更消息：{}", message);
        
        if ("delete".equals(operation)) {
            // 删除本地缓存
            localCache.invalidate(key);
            log.debug("删除本地缓存：{}", key);
        } else if ("update".equals(operation)) {
            // 更新本地缓存，需要从Redis获取最新值
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json)) {
                // 这里需要知道具体类型，简化处理，直接失效
                localCache.invalidate(key);
                log.debug("失效本地缓存，等待下次查询更新：{}", key);
            }
        }
    }
    
    /**
     * 添加到布隆过滤器
     */
    public void addToBloomFilter(String key) {
        getBloomFilterForKey(key).add(key);
    }
    
    /**
     * 根据key获取合适的布隆过滤器
     * 根据key的前缀决定使用哪个布隆过滤器
     * @param key 缓存键
     * @return 布隆过滤器
     */
    private BloomFilterStrategy<String> getBloomFilterForKey(String key) {
        if (key.startsWith(CACHE_SHOP_KEY)) {
            return bloomFilterFactory.getFilter(SHOP_BLOOM_FILTER_NAME);
        }
        return bloomFilterFactory.getFilter(DEFAULT_BLOOM_FILTER_NAME);
    }
    
    /**
     * 获取锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
    
    /**
     * 获取本地缓存统计信息
     */
    public String getLocalCacheStats() {
        return localCache.stats().toString();
    }
    
    public void setWithLogicalExpire(String key, Object data, Duration ofSeconds) {
        setWithLogicalExpire(key, data, ofSeconds.getSeconds(), TimeUnit.SECONDS);
    }
}