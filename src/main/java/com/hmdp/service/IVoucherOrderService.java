package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    @NotNull
    @Transactional(rollbackFor = Exception.class)
    Result getResult(Long voucherId);

    @Transactional(rollbackFor = Exception.class)
    void createVoucherOrder(VoucherOrder voucherOrder);
}
