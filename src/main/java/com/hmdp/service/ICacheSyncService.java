package com.hmdp.service;

import com.hmdp.canal.CanalMessage;

/**
 * 缓存同步服务接口
 */
public interface ICacheSyncService {
    
    /**
     * 处理缓存同步消息
     * @param message Canal消息
     */
    void handleCacheSyncMessage(CanalMessage message);
} 