# 票闪学习项目
# JMeter压测秒杀接口指南

本文档提供使用JMeter对秒杀接口进行压力测试的指南，以便发现系统的塌陷区并配合Sentinel进行限流熔断保护。

## 1. 准备工作

### 1.1 下载安装JMeter

1. 从Apache JMeter官网下载最新版本的JMeter: https://jmeter.apache.org/download_jmeter.cgi
2. 解压下载的压缩包
3. 在bin目录下运行jmeter.bat (Windows) 或 jmeter.sh (Linux/Mac)

### 1.2 安装插件管理器

1. 下载JMeter Plugins Manager: https://jmeter-plugins.org/install/Install/
2. 将下载的jar文件放入JMeter的lib/ext目录
3. 重启JMeter
4. 在选项菜单中会出现"Plugins Manager"

## 2. 创建秒杀接口测试计划

### 2.1 创建测试计划

1. 打开JMeter，创建新的测试计划(Test Plan)
2. 设置测试计划名称为"秒杀接口压测"
3. 在测试计划上右键，添加"线程   组"(Thread Group)

### 2.2 配置线程组

针对不同并发用户数创建多个线程组，例如：

1.

2. | 线程数 | QPS  | 预估异常数（每分钟） | 异常率估计 |
      | ------ | ---- | -------------------- | ---------- |
   | 100    | 100  | 0                    | 0%         |
   | 200    | 600  | 0–5                  | 0–1%       |
   | 500    | 700  | 30–60                | 5–10%      |
   | 1000   | 1000 | 150–300              | 30%        |

### 2.3 添加HTTP请求采样器

对每个线程组，添加HTTP请求采样器：

1. 右键点击线程组 -> 添加 -> 采样器 -> HTTP请求
2. 配置基本信息:
  - 名称: "秒杀请求"
  - 协议: http
  - 服务器名称或IP: localhost
  - 端口号: 8083
  - 方法: POST
  - 路径: /api/voucher-order/seckill/1 (替换1为实际的优惠券ID)

3. 添加HTTP头信息:
  - 右键点击HTTP请求 -> 添加 -> 配置元件 -> HTTP信息头管理器
  - 添加以下头信息:
    - Name: Authorization, Value: {用户Token}
    - Name: Content-Type, Value: application/json

### 2.4 添加用户Token参数化

1. 右键点击线程组 -> 添加 -> 配置元件 -> CSV数据文件设置
2. 配置:
  - 名称: "用户Token"
  - 文件名: user_tokens.csv (提前准备包含多个用户Token的CSV文件)
  - 变量名: userToken
  - 是否允许引用空值: 否
  - 是否重新读取文件开始处: 是

3. 在HTTP头信息管理器中修改Authorization的值为: ${userToken}

### 2.5 添加监听器

为每个线程组添加以下监听器：

1. 聚合报告(Summary Report)
  - 右键点击线程组 -> 添加 -> 监听器 -> 聚合报告

2. 查看结果树(View Results Tree)
  - 右键点击线程组 -> 添加 -> 监听器 -> 查看结果树

3. 响应时间图(Response Time Graph)
  - 右键点击线程组 -> 添加 -> 监听器 -> jp@gc - Response Time Graph

## 3. 执行测试并收集结果

### 3.1 执行测试

1. 启动应用服务
2. 依次运行每个线程组（不要同时运行）
3. 观察监控面板和JMeter结果

### 3.2 收集结果

从JMeter的聚合报告中收集以下数据：

- 样本数量(Samples)
- 成功请求数(Successes)
- 失败请求数(Failures)
- 平均响应时间(Average)
- 最小响应时间(Min)
- 最大响应时间(Max)
- 吞吐量(Throughput)
- 测试持续时间

### 3.3 提交结果进行分析

将JMeter测试结果按照下面的JSON格式提交到性能分析API:

```json
[
  {
    "samples": 1000,
    "successes": 990,
    "failures": 10,
    "avgResponseTime": 120.5,
    "minResponseTime": 50,
    "maxResponseTime": 350,
    "duration": 60000,
    "threadCount": 50
  },
  {
    "samples": 4000,
    "successes": 3800,
    "failures": 200,
    "avgResponseTime": 180.3,
    "minResponseTime": 55,
    "maxResponseTime": 520,
    "duration": 60000,
    "threadCount": 200
  },
  {
    "samples": 10000,
    "successes": 8500,
    "failures": 1500,
    "avgResponseTime": 350.8,
    "minResponseTime": 60,
    "maxResponseTime": 1200,
    "duration": 60000,
    "threadCount": 500
  }
]
```

提交到API:
```
POST http://localhost:8081/performance/analyze
```

### 3.4 查看分析报告

调用API查看分析报告:
```
GET http://localhost:8081/performance/report
```

## 4. 根据分析报告优化Sentinel配置

根据性能分析报告中的建议，调整Sentinel配置：

1. 修改限流阈值为最大稳定QPS的80%
2. 调整熔断策略，设置慢调用比例阈值
3. 配置系统规则，避免系统过载

## 5. 重复测试验证优化效果

1. 调整Sentinel配置后，重新执行压测
2. 对比优化前后的结果
3. 继续调整配置，直到达到最佳效果

## 6. 测试结果与塌陷区分析

### 6.1 实际测试结果

通过对秒杀接口进行不同并发水平的压测，我们得到了以下数据：

| 线程数 | 循环次数 | Ramp-up(秒) | QPS  | 异常率(%) | 平均响应时间(ms) |
|:-----:|:-------:|:----------:|:----:|:--------:|:--------------:|
| 100   | 20      | 10         | 200  | 0        | -              |
| 200   | 20      | 15         | 600  | 0.5      | -              |
| 1000  | 20      | 20         | 984  | 47.53    | -              |

### 6.2 塌陷区定位与分析

根据测试数据，我们可以确定系统的性能特征：

1. **稳定运行区**：0-200 QPS，系统完全稳定，无错误
2. **警告区**：200-600 QPS，系统出现极少量错误，但仍能正常工作
3. **塌陷起点**：约600-700 QPS，错误率开始明显上升
4. **深度塌陷区**：900 QPS以上，错误率急剧上升至近50%

### 6.3 优化后的Sentinel配置

根据测试结果，我们调整了Sentinel配置：

1. **流控阈值**：480 QPS（塌陷起点600的80%）
2. **熔断阈值**：
  - 慢调用响应时间：150ms
  - 慢调用比例：30%
  - 异常比例：5%
3. **系统保护**：
  - 系统最大QPS：700
  - CPU使用率上限：80%

### 6.4 查看塌陷区数据

可以通过系统API查看详细的塌陷区分析：

```
GET http://localhost:8083/api/performance/collapse-point
```

响应示例：
```json
{
  "success": true,
  "data": {
    "stablePoint": 200,
    "warningPoint": 600,
    "collapsePoint": 700,
    "deepCollapsePoint": 984,
    "sentinelConfig": {
      "flowLimit": 480,
      "degradeSlowRt": 150,
      "degradeExceptionRatio": 0.05,
      "systemQpsLimit": 700
    },
    "analysis": "系统在QPS达到600时开始出现少量错误(0.5%)，这是警告点；真正的塌陷起点估计在700 QPS附近，此时错误率开始快速上升；当QPS达到984(线程数1000)时，错误率高达47.53%，系统已处于深度塌陷状态。因此，建议将限流阈值设为480(600的80%)，确保系统稳定运行。"
  },
  "message": "success"
}
```