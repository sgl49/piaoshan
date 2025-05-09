package com.hmdp.canal;

import lombok.Data;
import java.util.Map;

/**
 * Canal消息对象
 */
@Data
public class CanalMessage {
    /**
     * 表名
     */
    private String table;
    
    /**
     * 操作类型：insert, update, delete
     */
    private String type;
    
    /**
     * 数据变更时间戳
     */
    private long timestamp;
    
    /**
     * 变更后的数据
     */
    private Map<String, String> data;
    
    /**
     * 变更前的数据（仅对update操作有效）
     */
    private Map<String, String> old;
} 