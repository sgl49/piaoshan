package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "canal")
public class CanalConfig {
    private String hostname;
    private Integer port;
    private String destination;
    private String username;
    private String password;
    private Integer batchSize;
}