package com.hmdp.service;

/**
 * 缓存预热服务接口 - 校招版
 * 专注于博客点赞排行榜预热
 */
public interface ICacheWarmUpService {
    /**
     * 预热博客点赞排行榜缓存
     * @return 预热结果
     */
    boolean warmUpBlogLikedRankCache();
} 