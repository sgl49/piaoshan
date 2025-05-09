package com.hmdp.config;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据源上下文持有者
 * 使用ThreadLocal保存当前线程的数据源类型
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT_HOLDER = new ThreadLocal<>();
    
    /**
     * 从库轮询计数器
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(-1);
    
    /**
     * 设置数据源类型
     */
    public static void setDataSourceType(DataSourceType dataSourceType) {
        CONTEXT_HOLDER.set(dataSourceType);
    }
    
    /**
     * 获取数据源类型
     */
    public static DataSourceType getDataSourceType() {
        return CONTEXT_HOLDER.get() == null ? DataSourceType.MASTER : CONTEXT_HOLDER.get();
    }
    
    /**
     * 清除数据源类型
     */
    public static void clearDataSourceType() {
        CONTEXT_HOLDER.remove();
    }
    
    /**
     * 轮询获取从库数据源
     */
    public static DataSourceType getSlaveDataSource() {
        // 轮询
        int index = COUNTER.getAndIncrement() % 2;
        if (COUNTER.get() > 9999) {
            COUNTER.set(-1);
        }
        if (index == 0) {
            return DataSourceType.SLAVE1;
        }
        return DataSourceType.SLAVE2;
    }
} 