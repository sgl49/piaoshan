package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private JwtUtils jwtUtils;
    
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        
        // 3. 保存验证码到Redis，设置过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        
        // 4. 发送验证码（模拟）
        log.debug("发送短信验证码成功，验证码：{}", code);
        
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        
        // 2. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        
        // 3. 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        
        // 4. 判断用户是否存在
        if(user == null){
            // 不存在，创建新用户
            user = createUserWithPhone(phone);
        }
        
        // 5. 生成JWT令牌
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String token = jwtUtils.generateToken(userDTO);
        
        // 6. 删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        
        // 7. 返回JWT令牌
        return Result.ok(token);
    }
    
    /**
     * 登出功能
     * @return 结果
     */
    @Override
    public Result logout(HttpServletRequest request) {
        // 1. 获取请求头中的token
        String token = request.getHeader(jwtUtils.getHeader());
        
        // 2. 检查token是否存在
        if (token != null && !token.isEmpty()) {
            try {
                // 3. 计算token剩余有效期
                Date expirationDate = jwtUtils.getExpirationDateFromToken(token);
                long expireTime = expirationDate.getTime();
                long currentTime = System.currentTimeMillis();
                long remainingTime = expireTime - currentTime;
                
                // 4. 如果token仍然有效，加入黑名单
                if (remainingTime > 0) {
                    stringRedisTemplate.opsForValue().set(
                            JWT_BLACKLIST_PREFIX + token,
                            "1",
                            remainingTime,
                            TimeUnit.MILLISECONDS
                    );
                    log.debug("Token已加入黑名单: {}", token);
                }
            } catch (Exception e) {
                log.error("登出时发生错误", e);
            }
        }
        
        // 使用RequestContextHolder不需要手动清理UserHolder
        
        return Result.ok();
    }
    
    /**
     * 获取当前登录用户
     * @return 当前登录用户
     */
    @Override
    public Result me() {
        // 从ThreadLocal中获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        return Result.ok(user);
    }
    
    private User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        //生成随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 保存用户
        save(user);
        return user;
    }
}
