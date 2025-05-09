package com.hmdp.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/voucher-order")
@Slf4j
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    
    /**
     * 秒杀接口
     * 使用Sentinel进行限流和熔断保护
     * @param voucherId 券ID
     * @return 订单结果
     */
    @PostMapping("seckill/{id}")
    @SentinelResource(
            value = "seckillVoucher", // 资源名称，用于在控制台设置限流规则
            blockHandler = "seckillVoucherBlockHandler", // 限流时的处理方法
            fallback = "seckillVoucherFallback" // 熔断降级时的处理方法
    )
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
    
    /**
     * seckillVoucher的限流处理方法（当被Sentinel限流时触发）
     * @param voucherId 券ID
     * @param ex 限流异常
     * @return 限流结果
     */
    public Result seckillVoucherBlockHandler(Long voucherId, BlockException ex) {
        log.warn("秒杀接口被限流，voucherId: {}, 异常: {}", voucherId, ex.getClass().getSimpleName());
        return Result.fail("秒杀人数过多，请稍后再试");
    }
    
    /**
     * seckillVoucher的熔断降级处理方法（当接口出现异常时触发）
     * @param voucherId 券ID
     * @param throwable 异常
     * @return 降级结果
     */
    public Result seckillVoucherFallback(Long voucherId, Throwable throwable) {
        log.error("秒杀接口出现异常，触发熔断，voucherId: {}, 异常: {}", voucherId, throwable.getMessage());
        return Result.fail("服务器开小差了，请稍后再试");
    }
    
    /**
     * 查询秒杀订单结果
     * @param voucherId 券ID
     * @return 查询结果
     */
    @PostMapping("/result/{id}")
    public Result getResult(@PathVariable("id") Long voucherId) {
        return voucherOrderService.getResult(voucherId);
    }
}
