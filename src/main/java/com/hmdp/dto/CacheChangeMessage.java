package com.hmdp.dto;

import lombok.Data;

/**
 * 缓存变更消息
 */
@Data
public class CacheChangeMessage {
    private String key;
    private String operation;  // delete, update, insert
    private Object data;       // 新增/更新时的数据
    private long timestamp;
}