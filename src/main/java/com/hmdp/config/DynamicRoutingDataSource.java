package com.hmdp.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态路由数据源
 * 根据当前线程上下文选择使用主库或从库
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    /**
     * 获取当前线程的数据源类型
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceType();
    }
} 