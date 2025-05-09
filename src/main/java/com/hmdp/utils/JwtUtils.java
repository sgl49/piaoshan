package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类，用于生成和验证JWT令牌
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtUtils {
    
    private String secret;
    private long expiration;
    private String header;
    
    /**
     * 生成JWT令牌
     * @param userDTO 用户信息
     * @return JWT令牌
     */
    public String generateToken(UserDTO userDTO) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userDTO.getId());
        claims.put("nickName", userDTO.getNickName());
        claims.put("icon", userDTO.getIcon());
        
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
    
    /**
     * 从令牌中获取用户信息
     * @param token JWT令牌
     * @return 用户信息
     */
    public UserDTO getUserFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        UserDTO userDTO = new UserDTO();
        userDTO.setId(Long.valueOf(claims.get("id").toString()));
        userDTO.setNickName((String) claims.get("nickName"));
        userDTO.setIcon((String) claims.get("icon"));
        return userDTO;
    }
    
    /**
     * 验证令牌是否有效
     * @param token JWT令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            log.error("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 刷新令牌
     * @param token 原令牌
     * @return 新令牌
     */
    public String refreshToken(String token) {
        try {
            UserDTO userDTO = getUserFromToken(token);
            return generateToken(userDTO);
        } catch (Exception e) {
            log.error("刷新令牌失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取令牌的过期时间
     * @param token JWT令牌
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }
    
    /**
     * 从令牌中获取数据声明
     * @param token JWT令牌
     * @return 数据声明
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
} 