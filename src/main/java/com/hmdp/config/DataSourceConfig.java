package com.hmdp.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 多数据源配置类
 * 实现MySQL主从读写分离
 */
@Configuration
public class DataSourceConfig {

    /**
     * 主数据源(写操作)
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 从数据源1(读操作)
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave1")
    public DataSource slave1DataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 从数据源2(读操作)
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave2")
    public DataSource slave2DataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 动态数据源
     */
    @Primary
    @Bean
    public DataSource dynamicDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slave1DataSource") DataSource slave1DataSource,
            @Qualifier("slave2DataSource") DataSource slave2DataSource) {
        
        DynamicRoutingDataSource dynamicDataSource = new DynamicRoutingDataSource();
        
        // 设置默认数据源为主数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        // 设置数据源映射
        Map<Object, Object> dataSourceMap = new HashMap<>(3);
        dataSourceMap.put(DataSourceType.MASTER, masterDataSource);
        dataSourceMap.put(DataSourceType.SLAVE1, slave1DataSource);
        dataSourceMap.put(DataSourceType.SLAVE2, slave2DataSource);
        
        dynamicDataSource.setTargetDataSources(dataSourceMap);
        
        return dynamicDataSource;
    }
    
    /**
     * 配置事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(
            @Qualifier("dynamicDataSource") DataSource dynamicDataSource) {
        return new DataSourceTransactionManager(dynamicDataSource);
    }
} 