package com.hmdp.service.impl;

import com.hmdp.canal.CanalMessage;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.service.ICacheSyncService;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 缓存同步服务实现类
 */
@Slf4j
@Service
public class CacheSyncServiceImpl implements ICacheSyncService {

    private final CacheClient cacheClient;

    @Autowired
    public CacheSyncServiceImpl(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    /**
     * 监听优惠券表的变更事件
     */
    @RabbitListener(queues = RabbitMQConfig.CACHE_SYNC_QUEUE)
    public void handleCacheSyncMessage(CanalMessage message) {
        if (message == null) {
            return;
        }

        String table = message.getTable();
        String type = message.getType();
        
        log.info("收到缓存同步消息: table={}, type={}", table, type);
        
        try {
            // 根据表名处理不同的缓存
            switch (table) {
                case "tb_voucher":
                case "tb_seckill_voucher":
                    handleVoucherCache(message);
                    break;
                case "tb_voucher_order":
                    handleVoucherOrderCache(message);
                    break;
                default:
                    log.debug("忽略非目标表的变更: {}", table);
            }
        } catch (Exception e) {
            log.error("处理缓存同步消息异常: table={}, type={}, error={}", 
                    table, type, e.getMessage(), e);
        }
    }

    /**
     * 处理优惠券缓存
     */
    private void handleVoucherCache(CanalMessage message) {
        if (message.getData() == null) {
            return;
        }
        
        String id = message.getData().get("id");
        if (id == null) {
            log.warn("优惠券数据缺少ID字段: {}", message);
            return;
        }
        
        String type = message.getType();
        String cacheKey = VOUCHER_CACHE_KEY + id;
        
        // 根据操作类型处理缓存
        switch (type) {
            case "insert":
            case "update":
                // 删除缓存，让缓存在下次查询时重建
                log.info("删除优惠券缓存: {}", cacheKey);
                cacheClient.deleteCache(cacheKey);
                break;
            case "delete":
                // 删除缓存
                log.info("删除优惠券缓存: {}", cacheKey);
                cacheClient.deleteCache(cacheKey);
                break;
            default:
                log.warn("未知操作类型: {}", type);
        }
    }

    /**
     * 处理优惠券订单缓存
     */
    private void handleVoucherOrderCache(CanalMessage message) {
        if (message.getData() == null) {
            return;
        }
        
        String id = message.getData().get("id");
        String userId = message.getData().get("user_id");
        String voucherId = message.getData().get("voucher_id");
        
        if (id == null || userId == null || voucherId == null) {
            log.warn("优惠券订单数据字段不完整: {}", message);
            return;
        }
        
        String type = message.getType();
        
        // 删除相关缓存
        String orderCacheKey = VOUCHER_ORDER_CACHE_KEY + id;
        String userOrderCacheKey = VOUCHER_ORDER_CACHE_KEY + "user:" + userId;
        String voucherOrderCacheKey = VOUCHER_ORDER_CACHE_KEY + "voucher:" + voucherId;
        
        // 根据操作类型处理缓存
        switch (type) {
            case "insert":
            case "update":
            case "delete":
                // 删除相关缓存
                log.info("删除优惠券订单相关缓存");
                cacheClient.deleteCache(orderCacheKey);
                cacheClient.deleteCache(userOrderCacheKey);
                cacheClient.deleteCache(voucherOrderCacheKey);
                break;
            default:
                log.warn("未知操作类型: {}", type);
        }
    }
} 