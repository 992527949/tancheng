package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //    // 阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    // 获取消息队列中的订单信息 XGROUPREAD GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 判断是否成功
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 创建订单,解析list
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 获取订单信息
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    
                    handleVoucherOrder(voucherOrder);
                    // ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }
        private void handlePendingList() {
            while (true) {
                try {
                    // 获取pendinglist中的订单信息 XGROUPREAD GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 判断是否成功
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    // 创建订单,解析list
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    // 获取订单信息
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    // ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pendinglist异常", e);
                    // 休眠2秒
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }


//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true){
//                // 获取订单信息
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("一人一单，请勿重复提交");
            return;
        }
        // 获取代理对象（事务）
            //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //return proxy.createVoucherOrder(voucherId);
        try {
            createVoucherOrder(voucherOrder);
        }
        finally {
            // 释放锁
            lock.unlock();
        }
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 用户id
        Long userId = UserHolder.getUser().getId();
        //订单iD
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        //判断结果是否为0
        int r = result.intValue();
        if(r != 0)
        {
            // 不为零，报错，没有资格
            return Result.fail(r == 1 ? "库存不足" : "已经秒杀过了");
        }
        // 为0，有资格，已经保存完毕
        // 返回订单id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 用户id
//        Long userId = UserHolder.getUser().getId();
//        //执行lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
//        //判断结果是否为0
//        int r = result.intValue();
//        if(r != 0)
//        {
//            // 不为零，报错，没有资格
//            return Result.fail(r == 1 ? "库存不足" : "已经秒杀过了");
//        }
//        // 为0，有资格，把下单信息保存到阻塞队列
//        // TODO 保存到阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        //  放入阻塞队列
//        orderTasks.add(voucherOrder);
//        // 返回订单id
//        return Result.ok(orderId);
//    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            // 秒杀未开始
//            return Result.fail("秒杀未开始");
//        }
//        // 是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            // 秒杀未开始
//            return Result.fail("秒杀已结束");
//        }
//        // 是否还有库存
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 创建锁对象
/// /        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("一人一单，请勿重复提交");
//        }
//        // 获取代理对象（事务）
//            //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            //return proxy.createVoucherOrder(voucherId);
//        try {
//            return createVoucherOrder(voucherId);
//        }
//        finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }

@Transactional
public void createVoucherOrder(VoucherOrder voucherOrder) {
    // 一人一单
    Long userId = voucherOrder.getUserId();
    Long voucherId = voucherOrder.getVoucherId(); // 提取 voucherId
    // 查询订单是否存在
    int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
        log.error("一人一单，请勿重复提交");
        return;
    }
    // 库存递减
    boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
            .eq("voucher_id", voucherId).gt("stock",0) //where id = ? and stock > 0
            .update();
    if(!success){
        log.error("库存不足");
        return;
    }
    // 生成订单
    save(voucherOrder);
}

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 一人一单
//        Long userId = UserHolder.getUser().getId();
//
//        // 查询订单是否存在
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            return Result.fail("每人限购一张");
//        }
//
//        // 库存递减
//        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).gt("stock",0) //where id = ? and stock > 0
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//        // 生成订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        // 返回订单id
//        return Result.ok(orderId);
//    }
}
