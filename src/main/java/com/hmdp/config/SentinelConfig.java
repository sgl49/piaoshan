package com.hmdp.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * Sentinel配置类
 */
@Configuration
public class SentinelConfig {

    /**
     * 注册Sentinel AOP切面
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 注册Sentinel统一异常处理
     */
    @Bean
    public BlockExceptionHandler blockExceptionHandler() {
        return new BlockExceptionHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
                // 根据异常类型返回不同的提示语
                String msg = "请求被限流，请稍后再试";
                if (e instanceof FlowException) {
                    msg = "系统繁忙，请稍后再试";
                } else if (e instanceof DegradeException) {
                    msg = "服务降级，请稍后再试";
                } else if (e instanceof ParamFlowException) {
                    msg = "热点参数限流，请稍后再试";
                } else if (e instanceof SystemBlockException) {
                    msg = "系统规则限流，请稍后再试";
                } else if (e instanceof AuthorityException) {
                    msg = "权限不足，拒绝访问";
                }

                // 设置响应头
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                // 将响应写入输出流
                try (PrintWriter writer = response.getWriter()) {
                    Result result = Result.fail(msg);
                    new ObjectMapper().writeValue(writer, result);
                    writer.flush();
                }
            }
        };
    }
} 