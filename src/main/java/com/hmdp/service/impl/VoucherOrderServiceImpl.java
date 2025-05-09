package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.StockInitializer;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rabbitmq.client.Channel;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StockInitializer stockInitializer;
    
    /**
     * 自己注入自己为了获取代理对象 @Lazy 延迟注入 避免形成循环依赖
     */
    @Resource
    @Lazy
    private IVoucherOrderService voucherOrderService;
    
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> QUEUE_SECKILL_SCRIPT;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 添加最大重试次数常量
    private static final int MAX_RETRIES = 3;

    static {
        // 初始化原有脚本
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
        
        // 初始化队列脚本
        QUEUE_SECKILL_SCRIPT = new DefaultRedisScript<>();
        QUEUE_SECKILL_SCRIPT.setLocation(new ClassPathResource("queue_seckill.lua"));
        QUEUE_SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 获取消息重试次数
     * @param message 消息对象
     * @return 重试次数
     */
    private int getRetryCount(Message message) {
        Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
        if (retryCount == null) {
            retryCount = 0;
            message.getMessageProperties().getHeaders().put("x-retry-count", 1);
        } else {
            message.getMessageProperties().getHeaders().put("x-retry-count", retryCount + 1);
        }
        return retryCount;
    }

    /**
     * RabbitMQ消息监听器，处理秒杀订单
     * @param voucherOrder 优惠券订单
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void listenSeckillOrder(VoucherOrder voucherOrder, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("接收到秒杀订单消息：{}", voucherOrder);

            // 检查订单是否已经处理过（幂等性检查）
            Long userId = voucherOrder.getUserId();
            Long voucherId = voucherOrder.getVoucherId();
            // 使用多级缓存查询订单
            String cacheKey = VOUCHER_ORDER_CACHE_KEY + "user:" + userId + ":voucher:" + voucherId;
            
            // 先查多级缓存
            VoucherOrder existOrder = cacheClient.queryWithMultiLevelCache(
                cacheKey,
                userId + ":" + voucherId,
                VoucherOrder.class,
                k -> {
                    // 缓存未命中时查询数据库
                    return getBaseMapper().selectOne(
                        new LambdaQueryWrapper<VoucherOrder>()
                            .eq(VoucherOrder::getUserId, userId)
                            .eq(VoucherOrder::getVoucherId, voucherId)
                            .last("LIMIT 1")
                    );
                },
                VOUCHER_ORDER_CACHE_TTL,
                TimeUnit.SECONDS
            );

            if (existOrder != null && existOrder.getProcessStatus() != ORDER_STATUS_PENDING) {
                log.info("订单已处理，跳过重复处理，订单ID：{}", existOrder.getId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 处理订单
            handleVoucherOrder(voucherOrder);
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("订单处理成功，消息已确认：{}", deliveryTag);
        } catch (Exception e) {
            log.error("处理秒杀订单异常，订单ID：{}，异常：{}", voucherOrder.getId(), e.getMessage());
            try {
                // 判断消息是否已经被重试
                int retryCount = getRetryCount(message);
                if (retryCount < MAX_RETRIES) {
                    // 消息重试
                    channel.basicNack(deliveryTag, false, true);
                    log.info("消息重试，当前重试次数：{}，消息ID：{}", retryCount, deliveryTag);
                } else {
                    // 超过重试次数，进入死信队列
                    channel.basicNack(deliveryTag, false, false);
                    log.warn("消息重试次数超过上限，进入死信队列，消息ID：{}", deliveryTag);
                    // 可以考虑将失败订单信息保存到数据库中，方便后续处理
                    saveFailedOrder(voucherOrder);
                }
            } catch (IOException ex) {
                log.error("消息确认失败，消息ID：{}，异常：{}", deliveryTag, ex.getMessage());
            }
        }
    }

    /**
     * 保存失败的订单信息，更新状态为失败
     * @param voucherOrder 订单信息
     */
    private void saveFailedOrder(VoucherOrder voucherOrder) {
        // 检查订单是否已经存在
        VoucherOrder existOrder = getById(voucherOrder.getId());
        if (existOrder != null) {
            // 更新订单状态为失败
            update(new LambdaUpdateWrapper<VoucherOrder>()
                .eq(VoucherOrder::getId, voucherOrder.getId())
                .set(VoucherOrder::getProcessStatus, ORDER_STATUS_FAILED));
            log.warn("订单处理失败，已更新状态：{}", voucherOrder.getId());
        } else {
            // 保存新订单，状态为失败
            voucherOrder.setProcessStatus(ORDER_STATUS_FAILED);
            save(voucherOrder);
            log.warn("订单处理失败，保存到失败记录：{}", voucherOrder.getId());
        }
        
        // 更新缓存
        String cacheKey = VOUCHER_ORDER_CACHE_KEY + "user:" + voucherOrder.getUserId() + ":voucher:" + voucherOrder.getVoucherId();
        cacheClient.set(cacheKey, voucherOrder, VOUCHER_ORDER_CACHE_TTL, TimeUnit.SECONDS);
    }

    //处理订单
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象（兜底）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if (!isLock) {
            // 获取失败,返回错误或者重试
            log.error("获取锁失败，用户：{}", userId);
            return;
        }
        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 秒杀优惠券(使用库存队列)
     *
     * @param voucherId 券id
     * @return {@link Result}
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        UserDTO user = UserHolder.getUser();
        // 获取订单id
        Long orderId = redisIdWorker.nextId("order");
        
        // 执行lua脚本，从库存队列中取出库存
        Long res = stringRedisTemplate.execute(
                QUEUE_SECKILL_SCRIPT,
                Arrays.asList(SECKILL_QUEUE_KEY + voucherId, SECKILL_ORDER_KEY + voucherId),
                user.getId().toString(), orderId.toString(), voucherId.toString());
        
        // 判断结果是否为0
        int r = res.intValue();
        if (r != 0) {
            // 不为0 没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "禁止重复下单");
        }

        // 创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(user.getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setProcessStatus(ORDER_STATUS_PENDING);

        // 发送消息到RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                voucherOrder
        );
        log.info("发送秒杀消息到RabbitMQ，订单ID：{}", orderId);

        // 返回订单id
        return Result.ok(orderId);
    }

    @Override
    @NotNull
    @Transactional(rollbackFor = Exception.class)
    public Result getResult(Long voucherId) {
        // 获取用户ID
        Long userId = UserHolder.getUser().getId();
        
        // 使用多级缓存查询
        String cacheKey = VOUCHER_ORDER_CACHE_KEY + "user:" + userId + ":voucher:" + voucherId;
        VoucherOrder order = cacheClient.queryWithMultiLevelCache(
            cacheKey,
            userId + ":" + voucherId,
            VoucherOrder.class,
            k -> {
                // 缓存未命中时查询数据库
                return getBaseMapper().selectOne(
                    new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId)
                        .last("LIMIT 1")
                );
            },
            VOUCHER_ORDER_CACHE_TTL,
            TimeUnit.SECONDS
        );
        
        // 如果订单不存在，检查库存是否已经售罄
        if (order == null) {
            // 检查库存是否已经售罄
            if (stockInitializer.isStockEmpty(voucherId)) {
                return Result.fail("很抱歉，该券已售罄");
            }
            // 订单正在处理中
            return Result.ok("订单正在处理中，请稍后查询");
        }
        
        // 根据订单处理状态返回不同结果
        switch (order.getProcessStatus()) {
            case ORDER_STATUS_SUCCESS:
                return Result.ok(order.getId());
            case ORDER_STATUS_FAILED:
                return Result.fail("订单处理失败");
            case ORDER_STATUS_PENDING:
            default:
                return Result.ok("订单正在处理中，请稍后查询");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 检查库存并扣减（乐观锁）
        boolean isSuccess = seckillVoucherService.update(
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                        .gt(SeckillVoucher::getStock, 0)
                        .setSql("stock=stock-1"));
                        
        if (!isSuccess) {
            log.error("库存不足");
            // 设置库存为空标记
            stockInitializer.setStockEmpty(voucherOrder.getVoucherId());
            // 更新订单状态为失败
            voucherOrder.setProcessStatus(ORDER_STATUS_FAILED);
            this.save(voucherOrder);
            return;
        }
        
        // 创建订单，更新状态为成功
        voucherOrder.setProcessStatus(ORDER_STATUS_SUCCESS);
        this.save(voucherOrder);
        
        // 更新缓存
        String cacheKey = VOUCHER_ORDER_CACHE_KEY + "user:" + voucherOrder.getUserId() + ":voucher:" + voucherOrder.getVoucherId();
        cacheClient.set(cacheKey, voucherOrder, VOUCHER_ORDER_CACHE_TTL, TimeUnit.SECONDS);
        
        log.info("订单创建成功：{}", voucherOrder.getId());
    }
}