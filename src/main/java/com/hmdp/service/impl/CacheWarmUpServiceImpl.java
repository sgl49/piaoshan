package com.hmdp.service.impl;

import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.ICacheWarmUpService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热服务实现类 - 校招版
 * 专注于博客点赞排行榜预热功能
 */
@Slf4j
@Service
public class CacheWarmUpServiceImpl implements ICacheWarmUpService {
    
    @Resource
    private BlogMapper blogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 博客点赞排行榜缓存预热 - XXL-Job定时任务
     * 每隔2小时执行一次，提前预热排行榜数据
     */
    @XxlJob("blogLikedRankCacheWarmUpJob")
    @Override
    public boolean warmUpBlogLikedRankCache() {
        log.info("开始预热博客点赞排行榜缓存...");
        
        try {
            // 1. 从Redis中获取点赞排行榜数据（取前10名）
            String rankKey = "blog:liked:rank";
            Set<ZSetOperations.TypedTuple<String>> topEntries = 
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(rankKey, 0, 9);
            
            if (topEntries == null || topEntries.isEmpty()) {
                log.info("点赞排行榜为空，无需预热");
                return true;
            }
            
            // 2. 遍历排行榜数据，预热每个博客的详情和用户信息
            int count = 0;
            for (ZSetOperations.TypedTuple<String> entry : topEntries) {
                try {
                    String blogIdStr = entry.getValue();
                    if (blogIdStr == null) continue;
                    
                    Long blogId = Long.valueOf(blogIdStr);
                    Double score = entry.getScore(); // 点赞数
                    
                    // 3. 查询博客详情并预热
                    Blog blog = blogMapper.selectById(blogId);
                    if (blog != null) {
                        // 确保点赞数正确
                        if (score != null) {
                            blog.setLiked(score.intValue());
                        }
                        
                        // 4. 查询博客作者信息并预热
                        User user = userMapper.selectById(blog.getUserId());
                        if (user != null) {
                            // 设置作者信息到博客对象
                            blog.setName(user.getNickName());
                            blog.setIcon(user.getIcon());
                            
                            // 预热作者信息
                            String userKey = RedisConstants.LOGIN_USER_KEY + user.getId();
                            cacheClient.set(userKey, user, 24L, TimeUnit.HOURS);
                        }
                        
                        // 5. 预热博客详情
                        String blogKey = RedisConstants.BLOG_LIKED_KEY + blogId;
                        cacheClient.set(blogKey, blog, 4L, TimeUnit.HOURS);
                        count++;
                    }
                } catch (Exception e) {
                    // 单个博客预热失败不影响整体流程
                    log.error("预热博客数据异常: {}", e.getMessage());
                }
            }
            
            // 6. 延长点赞排行榜的过期时间（4小时）
            stringRedisTemplate.expire(rankKey, 4, TimeUnit.HOURS);
            
            log.info("博客点赞排行榜缓存预热完成，共预热{}个博客", count);
            return true;
        } catch (Exception e) {
            log.error("预热博客点赞排行榜缓存异常", e);
            return false;
        }
    }
} 