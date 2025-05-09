package com.hmdp.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel规则初始化配置类
 * 在应用启动时初始化Sentinel的限流规则、熔断规则和系统规则
 */
@Configuration
@Slf4j
public class SentinelRuleConfig {

    /**
     * 初始化Sentinel规则
     */
    @PostConstruct
    public void initSentinelRules() {
        log.info("初始化Sentinel规则...");
        initFlowRules();
        initDegradeRules();
        initSystemRules();
        log.info("Sentinel规则初始化完成");
    }
    
    /**
     * 初始化限流规则
     */
    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // 秒杀接口限流规则 - 基于JMeter测试数据优化
        FlowRule seckillRule = new FlowRule();
        seckillRule.setResource("seckillVoucher");  // 资源名称，与@SentinelResource注解中的value对应
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);  // 限流阈值类型，QPS
        seckillRule.setCount(480);  // 限流阈值，设置为塌陷点(600 QPS)的80%
        seckillRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP); // 预热模式
        seckillRule.setWarmUpPeriodSec(5); // 5秒预热时间，更快速应对突发流量
        
        rules.add(seckillRule);
        
        // 全局接口限流规则 - 基于JMeter测试数据优化
        FlowRule globalRule = new FlowRule();
        globalRule.setResource("/" + "voucher-order"); // 资源名称为接口路径前缀
        globalRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        globalRule.setCount(700); // 全局限制700 QPS，对应测试中的系统极限值
        
        rules.add(globalRule);
        
        // 设置限流规则
        FlowRuleManager.loadRules(rules);
        log.info("初始化限流规则完成，共{}条规则，秒杀接口限流阈值: {} QPS", rules.size(), seckillRule.getCount());
    }
    
    /**
     * 初始化熔断降级规则
     */
    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        
        // 秒杀接口熔断规则（慢调用比例）- 基于JMeter测试数据优化
        DegradeRule seckillSlowRule = new DegradeRule();
        seckillSlowRule.setResource("seckillVoucher");
        seckillSlowRule.setGrade(RuleConstant.DEGRADE_GRADE_RT); // 慢调用比例模式
        seckillSlowRule.setCount(150); // 超过150ms为慢调用，更快触发熔断保护
        seckillSlowRule.setTimeWindow(5); // 熔断时长，减少为5秒，更快恢复
        seckillSlowRule.setSlowRatioThreshold(0.3); // 慢调用比例阈值，超过30%触发熔断
        seckillSlowRule.setMinRequestAmount(50); // 最小请求数，降低为50，更快进行统计
        seckillSlowRule.setStatIntervalMs(1000); // 统计时间窗口，1秒
        
        rules.add(seckillSlowRule);
        
        // 秒杀接口熔断规则（异常比例）- 基于JMeter测试数据优化
        DegradeRule seckillExceptionRule = new DegradeRule();
        seckillExceptionRule.setResource("seckillVoucher");
        seckillExceptionRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO); // 异常比例模式
        seckillExceptionRule.setCount(0.05); // 异常比例阈值，降低至5%触发熔断，根据测试数据调整
        seckillExceptionRule.setTimeWindow(10); // 熔断时长，减少为10秒
        seckillExceptionRule.setMinRequestAmount(50); // 最小请求数，降低为50，更快进行统计
        seckillExceptionRule.setStatIntervalMs(1000); // 统计时间窗口，1秒
        
        rules.add(seckillExceptionRule);
        
        // 设置熔断规则
        DegradeRuleManager.loadRules(rules);
        log.info("初始化熔断规则完成，共{}条规则，异常比例阈值: {}%", rules.size(), seckillExceptionRule.getCount() * 100);
    }
    
    /**
     * 初始化系统保护规则
     */
    private void initSystemRules() {
        List<SystemRule> rules = new ArrayList<>();
        
        // 系统负载保护 - 基于JMeter测试数据优化
        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(3.0); // 当系统负载超过3时触发系统保护
        rules.add(loadRule);
        
        // CPU使用率保护 - 基于JMeter测试数据优化
        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.8); // 当CPU使用率超过80%时触发系统保护
        rules.add(cpuRule);
        
        // QPS保护 - 基于JMeter测试数据优化
        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(700); // 系统整体QPS不超过700，与实测塌陷区起点一致
        rules.add(qpsRule);
        
        // 设置系统规则
        SystemRuleManager.loadRules(rules);
        log.info("初始化系统保护规则完成，共{}条规则，系统最大QPS: {}", rules.size(), qpsRule.getQps());
    }
} 