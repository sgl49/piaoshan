package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 交换机名称
    public static final String EXCHANGE_NAME = "seckill.direct";
    // 路由键
    public static final String ROUTING_KEY = "seckill.order";
    // 队列名称
    public static final String QUEUE_NAME = "seckill.order.queue";

    // 死信交换机
    public static final String DEAD_LETTER_EXCHANGE = "seckill.dlx";
    // 死信队列
    public static final String DEAD_LETTER_QUEUE = "seckill.dlq";
    // 死信路由键
    public static final String DEAD_LETTER_ROUTING_KEY = "seckill.order.dlq";

    // Canal监听binlog相关配置
    public static final String BINLOG_EXCHANGE = "binlog.topic";
    public static final String CACHE_SYNC_QUEUE = "cache.sync.queue";
    public static final String VOUCHER_BINLOG_KEY = "binlog.voucher.#";
    public static final String VOUCHER_ORDER_BINLOG_KEY = "binlog.voucher_order.#";

    // 缓存更新相关配置
    public static final String CACHE_EXCHANGE = "cache.fanout";
    public static final String LOCAL_CACHE_QUEUE = "local.cache.queue";
    public static final String REDIS_CACHE_QUEUE = "redis.cache.queue";


    //1 第一个绑定:秒杀优惠券订单
    
    /**
     * 声明Direct交换机
     */
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(EXCHANGE_NAME, true, false);
    }

    /**
     * 声明队列
     */
    @Bean
    public Queue seckillQueue() {
        // 持久化队列，添加死信队列配置
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * 绑定队列和交换机
     */
    @Bean
    public Binding seckillBinding(Queue seckillQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with(ROUTING_KEY);
    }

    //2 第二个绑定:死信
    /**
     * 声明死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    /**
     * 声明死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }


    //3 第三个绑定:binlog
    /**
     * 声明Binlog主题交换机
     */
    @Bean
    public TopicExchange binlogExchange() {
        return new TopicExchange(BINLOG_EXCHANGE, true, false);
    }

    /**
     * 声明缓存同步队列
     */
    @Bean
    public Queue cacheSyncQueue() {
        return QueueBuilder.durable(CACHE_SYNC_QUEUE).build();
    }

    /**
     * 绑定优惠券Binlog消息到缓存同步队列
     */
    @Bean
    public Binding voucherBinlogBinding() {
        return BindingBuilder.bind(cacheSyncQueue())
                .to(binlogExchange())
                .with(VOUCHER_BINLOG_KEY);
    }

    /**
     * 绑定优惠券订单Binlog消息到缓存同步队列
     */
    @Bean
    public Binding voucherOrderBinlogBinding() {
        return BindingBuilder.bind(cacheSyncQueue())
                .to(binlogExchange())
                .with(VOUCHER_ORDER_BINLOG_KEY);
    }
     // 4 第四个绑定:本地缓存和redis缓存
    /**
     * 声明缓存更新广播交换机
     */
    @Bean
    public FanoutExchange cacheExchange() {
        return new FanoutExchange(CACHE_EXCHANGE, true, false);
    }

    /**
     * 声明本地缓存更新队列
     */
    @Bean
    public Queue localCacheQueue() {
        return QueueBuilder.durable(LOCAL_CACHE_QUEUE).build();
    }

    /**
     * 声明Redis缓存更新队列
     */
    @Bean
    public Queue redisCacheQueue() {
        return QueueBuilder.durable(REDIS_CACHE_QUEUE).build();
    }

    /**
     * 绑定本地缓存队列到缓存交换机
     */
    @Bean
    public Binding localCacheBinding() {
        return BindingBuilder.bind(localCacheQueue()).to(cacheExchange());
    }

    /**
     * 绑定Redis缓存队列到缓存交换机
     */
    @Bean
    public Binding redisCacheBinding() {
        return BindingBuilder.bind(redisCacheQueue()).to(cacheExchange());
    }



    /**
     * 配置消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);

        // 消息发送失败返回到队列中
        rabbitTemplate.setMandatory(true);

        // 消息确认回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.out.println("消息发送失败：" + cause);
            }
        });

        // 消息返回回调
        rabbitTemplate.setReturnsCallback(returned -> {
            System.out.println("消息发送失败，返回消息：" + returned.getMessage());
        });

        return rabbitTemplate;
    }
}