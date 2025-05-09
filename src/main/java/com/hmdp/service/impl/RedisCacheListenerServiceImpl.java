package com.hmdp.service.impl;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.CacheChangeMessage;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;

/**
 * Redis缓存监听服务
 * 用于处理缓存变更消息，确保Redis集群中的缓存一致性
 */
@Slf4j
@Service
public class RedisCacheListenerServiceImpl {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @RabbitListener(queues = RabbitMQConfig.REDIS_CACHE_QUEUE)
    public void handleRedisCacheMessage(CacheChangeMessage message) {
        if (message == null) {
            return;
        }

        String key = message.getKey();
        String operation = message.getOperation();
        Object data = message.getData();

        log.debug("收到Redis缓存变更消息: key={}, operation={}", key, operation);

        try {
            switch (operation) {
                case "delete":
                    // 删除Redis缓存
                    stringRedisTemplate.delete(key);
                    log.info("已删除Redis缓存: {}", key);
                    break;
                case "update":
                case "insert":
                    if (data != null) {
                        // 更新或新增Redis缓存
                        cacheClient.setWithLogicalExpire(
                                key,
                                data,
                                Duration.ofSeconds(1800)  // 30分钟过期
                        );
                        log.info("已更新Redis缓存: {}", key);
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("处理Redis缓存变更消息异常: key={}, operation={}, error={}",
                    key, operation, e.getMessage());
        }
    }
}