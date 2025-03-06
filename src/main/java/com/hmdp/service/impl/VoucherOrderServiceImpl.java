package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 秒杀未开始
            return Result.fail("秒杀未开始");
        }
        // 是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀未开始
            return Result.fail("秒杀已结束");
        }
        // 是否还有库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1000);
        if (!isLock) {
            return Result.fail("一人一单，请勿重复提交");
        }
        // 获取代理对象（事务）
            //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //return proxy.createVoucherOrder(voucherId);
        try {
            return createVoucherOrder(voucherId);
        }
        finally {
            // 释放锁
            lock.unlock();
        }


    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();

        // 查询订单是否存在
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("每人限购一张");
        }

        // 库存递减
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0) //where id = ? and stock > 0
                .update();
        if(!success){
            return Result.fail("库存不足");
        }
        // 生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 返回订单id
        return Result.ok(orderId);

    }
}
