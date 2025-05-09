package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.password}")
    private String password;
    
    @Value("${spring.redis.sentinel.master}")
    private String master;
    
    @Value("${spring.redis.sentinel.nodes}")
    private String[] nodes;

    @Bean
    public RedissonClient redissonClient() {
        // 创建配置
        Config config = new Config();
        
        // 使用Sentinel模式
        config.useSentinelServers()
                .setMasterName(master)
                .setPassword(password)
                .setDatabase(0)
                // 设置连接池大小
                .setMasterConnectionMinimumIdleSize(5)
                .setMasterConnectionPoolSize(10);
        
        // 添加哨兵节点
        for (String node : nodes) {
            // 格式转换，添加redis://前缀
            String redisNode = "redis://" + node;
            config.useSentinelServers().addSentinelAddress(redisNode);
        }

        // 创建Redisson客户端
        return Redisson.create(config);
    }
}