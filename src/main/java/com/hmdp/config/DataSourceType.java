package com.hmdp.config;

/**
 * 数据源类型枚举
 */
public enum DataSourceType {
    /**
     * 主库(写)
     */
    MASTER,
    
    /**
     * 从库1(读)
     */
    SLAVE1,
    
    /**
     * 从库2(读)
     */
    SLAVE2
} 