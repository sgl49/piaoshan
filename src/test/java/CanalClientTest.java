import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry.*;
import com.alibaba.otter.canal.protocol.Message;

import java.net.InetSocketAddress;
import java.util.List;

public class CanalClientTest {

    public static void main(String[] args) {
        // 1. 创建 Canal 连接器
        CanalConnector connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress("127.0.0.1", 11111), // Canal 服务端地址
                "example",  // 实例名称（与 canal.destination 配置一致）
                "canal",    // 用户名（如果 Canal 服务端配置了密码）
                "canal"     // 密码
        );

        try {
            // 2. 建立连接
            connector.connect();
            // 3. 订阅数据库和表（监听 hmdp 库的所有表）
            connector.subscribe("hmdp\\..*");
            // 4. 回滚到未消费的位置（首次启动从最新位置开始）
            connector.rollback();

            while (true) {
                // 5. 批量获取数据变更（100 条）
                Message message = connector.getWithoutAck(100);
                long batchId = message.getId();
                int size = message.getEntries().size();

                if (batchId == -1 || size == 0) {
                    Thread.sleep(1000); // 无数据时休眠
                } else {
                    // 6. 解析 Binlog 变更
                    parseEntries(message.getEntries());
                }

                // 7. 确认已消费（提交位点）
                connector.ack(batchId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connector.disconnect();
        }
    }

    private static void parseEntries(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.getEntryType() == EntryType.TRANSACTIONBEGIN ||
                    entry.getEntryType() == EntryType.TRANSACTIONEND) {
                continue; // 忽略事务开始/结束事件
            }

            RowChange rowChange;
            try {
                rowChange = RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("解析 Binlog 失败", e);
            }

            EventType eventType = rowChange.getEventType();
            String tableName = entry.getHeader().getTableName();
            String logType = entry.getHeader().getEventType().name();

            System.out.println("\n========= 监听到变更 =========");
            System.out.printf("数据库: %s, 表: %s, 操作类型: %s\n",
                    entry.getHeader().getSchemaName(),
                    tableName,
                    eventType);

            // 打印变更的每一行数据
            for (RowData rowData : rowChange.getRowDatasList()) {
                if (eventType == EventType.DELETE) {
                    printColumns(rowData.getBeforeColumnsList());
                } else if (eventType == EventType.INSERT) {
                    printColumns(rowData.getAfterColumnsList());
                } else {
                    System.out.println("------ 变更前数据 ------");
                    printColumns(rowData.getBeforeColumnsList());
                    System.out.println("------ 变更后数据 ------");
                    printColumns(rowData.getAfterColumnsList());
                }
            }
        }
    }

    private static void printColumns(List<Column> columns) {
        for (Column column : columns) {
            System.out.printf("%s: %s (%s更新)\n",
                    column.getName(),
                    column.getValue(),
                    column.getUpdated() ? "已" : "未");
        }
    }
}
