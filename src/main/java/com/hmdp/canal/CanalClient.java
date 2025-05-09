package com.hmdp.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;
import com.hmdp.config.CanalConfig;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.CacheChangeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Canal客户端，用于监听MySQL binlog并发送到MQ
 */
@Slf4j
@Component
public class CanalClient implements ApplicationRunner, DisposableBean {

    private final CanalConfig canalConfig;
    private final RabbitTemplate rabbitTemplate;
    private CanalConnector connector;
    private volatile boolean running = false;
    private Thread thread;

    @Autowired
    public CanalClient(CanalConfig canalConfig, RabbitTemplate rabbitTemplate) {
        this.canalConfig = canalConfig;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 启动Canal客户端
     */
    public void start() {
        thread = new Thread(this::process);
        thread.setDaemon(true);
        thread.setName("canal-client-thread");
        running = true;
        thread.start();
    }

    /**
     * 停止Canal客户端
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                log.error("停止Canal客户端线程时发生错误", e);
                Thread.currentThread().interrupt();
            }
        }
        closeConnection();
    }

    /**
     * 关闭连接
     */
    private void closeConnection() {
        if (connector != null) {
            connector.disconnect();
            connector = null;
        }
    }

    /**
     * 处理Binlog消息
     */
    private void process() {
        while (running) {
            try {
                // 建立连接
                connector = CanalConnectors.newSingleConnector(
                        new InetSocketAddress(
                                canalConfig.getHostname(),
                                canalConfig.getPort()),
                        canalConfig.getDestination(),
                        canalConfig.getUsername(),
                        canalConfig.getPassword());
                connector.connect();
                // 订阅数据库表
                connector.subscribe("hmdp\\.tb_.*");
                // 回滚到未进行ack的地方
                connector.rollback();
                
                log.info("Canal客户端启动成功，开始监听MySQL binlog变化...");
                
                while (running) {
                    // 获取指定数量的数据
                    Message message = connector.getWithoutAck(canalConfig.getBatchSize());
                    long batchId = message.getId();
                    if (batchId == -1 || message.getEntries().isEmpty()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    
                    // 处理消息
                    processMessage(message);
                    
                    // 提交确认
                    connector.ack(batchId);
                }
            } catch (Exception e) {
                log.error("Canal客户端处理消息发生异常", e);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } finally {
                closeConnection();
            }
        }
    }

    /**
     * 处理Canal消息
     */
    private void processMessage(Message message) {
        for (Entry entry : message.getEntries()) {
            if (entry.getEntryType() == EntryType.ROWDATA) {
                try {
                    RowChange rowChange = RowChange.parseFrom(entry.getStoreValue());
                    String tableName = entry.getHeader().getTableName();
                    String schemaName = entry.getHeader().getSchemaName();
                    EventType eventType = rowChange.getEventType();

                    for (RowData rowData : rowChange.getRowDatasList()) {
                        // 构建缓存键
                        String cacheKey = buildCacheKey(tableName, rowData);

                        // 构建缓存变更消息
                        CacheChangeMessage cacheMessage = new CacheChangeMessage();
                        cacheMessage.setKey(cacheKey);
                        cacheMessage.setTimestamp(System.currentTimeMillis());

                        switch (eventType) {
                            case INSERT:
                                cacheMessage.setOperation("insert");
                                cacheMessage.setData(convertColumns(rowData.getAfterColumnsList()));
                                break;
                            case UPDATE:
                                cacheMessage.setOperation("update");
                                cacheMessage.setData(convertColumns(rowData.getAfterColumnsList()));
                                break;
                            case DELETE:
                                cacheMessage.setOperation("delete");
                                break;
                        }

                        // 发送到RabbitMQ
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.CACHE_EXCHANGE,
                                "cache." + tableName,
                                cacheMessage
                        );
                    }
                } catch (Exception e) {
                    log.error("处理Canal消息时发生异常", e);
                }
            }
        }
    }

    private String buildCacheKey(String tableName, RowData rowData) {
        switch (tableName) {
            case "tb_voucher":
                return "cache:voucher:" + getColumnValue(rowData, "id");
            case "tb_seckill_voucher":
                return "cache:seckill:voucher:" + getColumnValue(rowData, "id");
            case "tb_voucher_order":
                return "cache:voucher:order:" + getColumnValue(rowData, "id");
            default:
                return null;
        }
    }

    private String getColumnValue(RowData rowData, String columnName) {
        for (Column column : rowData.getAfterColumnsList()) {
            if (column.getName().equals(columnName)) {
                return column.getValue();
            }
        }
        return null;
    }

    /**
     * 判断是否是需要处理的表
     */
    private boolean isTargetTable(String tableName) {
        // 只处理优惠券和优惠券订单表
        return "tb_seckill_voucher".equals(tableName) || 
               "tb_voucher".equals(tableName) ||
               "tb_voucher_order".equals(tableName);
    }

    /**
     * 构建路由键
     */
    private String buildRoutingKey(String tableName, EventType eventType) {
        String tableType = tableName.replace("tb_", "");
        // 针对优惠券订单表特殊处理
        if ("voucher_order".equals(tableType)) {
            tableType = "voucher_order";
        } else if ("seckill_voucher".equals(tableType)) {
            tableType = "voucher";  // 秒杀券也归类到voucher
        }
        
        return "binlog." + tableType + "." + eventType.name().toLowerCase();
    }

    /**
     * 构建消息体
     */
    private CanalMessage buildMessage(String tableName, EventType eventType, RowData rowData) {
        CanalMessage message = new CanalMessage();
        message.setTable(tableName);
        message.setType(eventType.name().toLowerCase());
        message.setTimestamp(System.currentTimeMillis());
        
        // 根据事件类型处理数据
        switch (eventType) {
            case INSERT:
                message.setData(convertColumns(rowData.getAfterColumnsList()));
                break;
            case UPDATE:
                message.setData(convertColumns(rowData.getAfterColumnsList()));
                message.setOld(convertColumns(rowData.getBeforeColumnsList()));
                break;
            case DELETE:
                message.setData(convertColumns(rowData.getBeforeColumnsList()));
                break;
            default:
                break;
        }
        
        return message;
    }

    /**
     * 转换列数据为键值对
     */
    private java.util.Map<String, String> convertColumns(List<Column> columns) {
        java.util.Map<String, String> result = new java.util.HashMap<>(columns.size());
        for (Column column : columns) {
            result.put(column.getName(), column.getValue());
        }
        return result;
    }

    /**
     * Spring应用启动时自动调用
     */
    @Override
    public void run(ApplicationArguments args) {
        this.start();
    }

    /**
     * Spring应用停止时自动调用
     */
    @Override
    public void destroy() {
        this.stop();
    }
} 