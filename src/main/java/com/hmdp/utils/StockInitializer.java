package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 库存队列初始化工具类
 */
@Slf4j
@Component
public class StockInitializer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_QUEUE_KEY = "seckill:queue:";
    private static final String STOCK_EMPTY_KEY = "seckill:empty:";

    /**
     * 初始化库存队列
     * @param voucherId 优惠券ID
     * @param stock 库存数量
     */
    public void initStockQueue(Long voucherId, Integer stock) {
        String queueKey = STOCK_QUEUE_KEY + voucherId;
        // 清除之前的库存队列(如果存在)
        stringRedisTemplate.delete(queueKey);
        // 创建库存队列
        for (int i = 0; i < stock; i++) {
            stringRedisTemplate.opsForList().rightPush(queueKey, "1");
        }
        log.info("初始化库存队列成功，优惠券ID: {}，库存数量: {}", voucherId, stock);
        
        // 重置库存为空标记
        stringRedisTemplate.delete(STOCK_EMPTY_KEY + voucherId);
    }

    /**
     * 设置库存为空的标记
     * @param voucherId 优惠券ID
     */
    public void setStockEmpty(Long voucherId) {
        stringRedisTemplate.opsForValue().set(STOCK_EMPTY_KEY + voucherId, "1");
        log.info("设置库存为空标记，优惠券ID: {}", voucherId);
    }

    /**
     * 检查库存是否为空
     * @param voucherId 优惠券ID
     * @return 是否为空
     */
    public boolean isStockEmpty(Long voucherId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(STOCK_EMPTY_KEY + voucherId));
    }
} 