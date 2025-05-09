package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器
 * 主要功能：验证用户是否已登录
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从ThreadLocal中获取用户信息
        if (UserHolder.getUser() == null) {
            // 用户未登录，返回401
            log.debug("用户未登录，请求被拦截: {}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        
        // 用户已登录，放行
        return true;
    }
} 