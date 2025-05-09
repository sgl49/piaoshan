package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * JWT令牌刷新拦截器
 * 主要功能：
 * 1. 提取并验证令牌
 * 2. 存储用户信息到ThreadLocal
 * 3. 自动刷新临近过期的令牌
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    
    // token刷新阈值，当令牌剩余时间小于这个值时自动刷新（默认30分钟）
    private static final long REFRESH_THRESHOLD = RedisConstants.JWT_REFRESH_THRESHOLD;
    
    // 黑名单前缀，用于存储已废弃但尚未过期的token
    private static final String BLACKLIST_PREFIX = RedisConstants.JWT_BLACKLIST_PREFIX;
    
    public RefreshTokenInterceptor(JwtUtils jwtUtils, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtils = jwtUtils;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader(jwtUtils.getHeader());
        
        // 2. 检查token是否存在，不存在直接放行
        if (token == null || token.isEmpty()) {
            return true;
        }
        
        // 3. 检查token是否在黑名单中
        String blacklistKey = BLACKLIST_PREFIX + token;
        Boolean isInBlacklist = stringRedisTemplate.hasKey(blacklistKey);
        if (Boolean.TRUE.equals(isInBlacklist)) {
            log.debug("Token在黑名单中，拒绝访问: {}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }
        
        // 4. 验证token有效性
        if (!jwtUtils.validateToken(token)) {
            return true; // token无效，交给后续拦截器处理
        }
        
        // 5. 解析token中的用户信息
        UserDTO user = jwtUtils.getUserFromToken(token);
        
        // 6. 存入ThreadLocal
        UserHolder.saveUser(user);
        
        // 7. 检查token是否需要刷新（过期时间小于阈值）
        try {
            long expireTime = jwtUtils.getExpirationDateFromToken(token).getTime();
            long currentTime = System.currentTimeMillis();
            if (expireTime - currentTime < REFRESH_THRESHOLD) {
                // 生成新token
                String newToken = jwtUtils.refreshToken(token);
                // 将新token放入响应头
                response.setHeader(jwtUtils.getHeader(), newToken);
                
                // 将旧token加入黑名单（使用旧token的过期时间作为黑名单中的过期时间）
                long remainingTime = expireTime - currentTime;
                if (remainingTime > 0) {
                    stringRedisTemplate.opsForValue().set(
                            blacklistKey,
                            "1",
                            remainingTime,
                            TimeUnit.MILLISECONDS
                    );
                }
                
                log.debug("令牌已刷新: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("刷新令牌出错", e);
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 使用RequestContextHolder不需要手动清理，请求结束时会自动清理
    }
} 