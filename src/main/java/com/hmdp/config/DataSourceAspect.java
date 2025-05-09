package com.hmdp.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hmdp.annotation.Master;
import com.hmdp.annotation.Slave;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * 数据源AOP切面
 * 根据Service方法名自动切换数据源
 */
@Aspect
@Order(1) // 确保该AOP在事务AOP之前执行
@Component
@Slf4j
public class DataSourceAspect {

    /**
     * 定义切入点：所有service包下的所有方法
     */
    @Pointcut("execution(* com.hmdp.service.impl.*.*(..))")
    public void servicePointcut() {}
    
    /**
     * 环绕增强，根据方法名或注解判断使用主库还是从库
     */
    @Around("servicePointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        
        try {
            // 判断是否有注解
            if (method.isAnnotationPresent(Master.class)) {
                // 有@Master注解，强制使用主库
                DataSourceContextHolder.setDataSourceType(DataSourceType.MASTER);
                log.debug("@Master注解，强制使用主库数据源");
            } else if (method.isAnnotationPresent(Slave.class)) {
                // 有@Slave注解，强制使用从库
                DataSourceContextHolder.setDataSourceType(DataSourceContextHolder.getSlaveDataSource());
                log.debug("@Slave注解，强制使用从库数据源: {}", DataSourceContextHolder.getDataSourceType());
            } else {
                // 根据方法名前缀判断是读还是写操作
                String methodName = method.getName();
                if (isReadMethod(methodName)) {
                    // 读操作，使用从库
                    DataSourceContextHolder.setDataSourceType(DataSourceContextHolder.getSlaveDataSource());
                    log.debug("根据方法名，切换到从库数据源: {}", DataSourceContextHolder.getDataSourceType());
                } else {
                    // 写操作，使用主库
                    DataSourceContextHolder.setDataSourceType(DataSourceType.MASTER);
                    log.debug("根据方法名，切换到主库数据源");
                }
            }
            
            return point.proceed();
        } finally {
            // 清除数据源配置
            DataSourceContextHolder.clearDataSourceType();
            log.debug("清除数据源配置");
        }
    }
    
    /**
     * 判断是否为读方法
     */
    private boolean isReadMethod(String methodName) {
        return methodName.startsWith("get") 
                || methodName.startsWith("query") 
                || methodName.startsWith("find") 
                || methodName.startsWith("list") 
                || methodName.startsWith("count") 
                || methodName.startsWith("select") 
                || methodName.startsWith("check");
    }
} 