package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_QUEUE_KEY = "seckill:queue:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String SECKILL_EMPTY_KEY = "seckill:empty:";
    
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String CACHE_TYPE_KEY = "cache:type";
    
    // 多级缓存相关常量
    public static final String VOUCHER_CACHE_KEY = "cache:voucher:";
    public static final Long VOUCHER_CACHE_TTL = 60L;
    public static final String VOUCHER_ORDER_CACHE_KEY = "cache:voucher:order:";
    public static final Long VOUCHER_ORDER_CACHE_TTL = 30L;
    
    // 缓存变更通知相关常量
    public static final String CACHE_CHANGE_TOPIC = "cache:change";
    public static final String CACHE_DELETE_KEY = "cache:delete:";
    
    // JWT相关常量
    public static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";
    public static final long JWT_REFRESH_THRESHOLD = 30 * 60 * 1000; // 30分钟
    
    // 订单状态常量
    public static final int ORDER_STATUS_PENDING = 0; // 订单处理中
    public static final int ORDER_STATUS_SUCCESS = 1; // 订单成功
    public static final int ORDER_STATUS_FAILED = 2; // 订单失败
    
    // 业务限流常量
    public static final String LIMIT_RATE_KEY = "limit:rate:";
    public static final String LIMIT_BUCKET_KEY = "limit:bucket:";
    
    // 缓存预热任务
    public static final String CACHE_WARM_UP_TASK_KEY = "cache:warm:up:task";
    
    // 数据访问统计
    public static final String DATA_ACCESS_STATS_KEY = "stats:access:";
}
