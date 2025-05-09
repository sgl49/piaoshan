package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.bloom.BloomFilterFactory;
import com.hmdp.utils.bloom.BloomFilterStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 布隆过滤器初始化器
 * 在应用启动时预热布隆过滤器，加载商品数据
 */
@Component
public class BloomFilterInitializer implements CommandLineRunner {
    private final IShopService shopService;
    private final BloomFilterFactory bloomFilterFactory;

    private static final String SHOP_KEY_PREFIX = "cache:shop:";
    private static final String SHOP_BLOOM_FILTER_NAME = "shop";
    private static final Logger log = LoggerFactory.getLogger(BloomFilterInitializer.class);

    public BloomFilterInitializer(IShopService shopService, BloomFilterFactory bloomFilterFactory) {
        this.shopService = shopService;
        this.bloomFilterFactory = bloomFilterFactory;
    }

    @Override
    public void run(String... args) {
        // 获取店铺布隆过滤器
        BloomFilterStrategy<String> shopFilter = bloomFilterFactory.getFilter(SHOP_BLOOM_FILTER_NAME);
        
        // 查询所有店铺并添加到布隆过滤器
        List<Shop> shops = shopService.list();
        int count = 0;
        
        log.info("开始初始化店铺布隆过滤器，共有 {} 家店铺", shops.size());
        
        for (Shop shop : shops) {
            String key = SHOP_KEY_PREFIX + shop.getId();
            shopFilter.add(key);
            count++;
            
            // 每100条记录输出一次日志
            if (count % 100 == 0) {
                log.info("布隆过滤器初始化进度: {}/{}", count, shops.size());
            }
        }
        
        log.info("布隆过滤器初始化完成，共添加 {} 家店铺", count);
    }
}