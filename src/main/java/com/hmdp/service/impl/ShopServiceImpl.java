package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.redisson.api.GeoUnit;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        // 1 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_POOL = Executors.newFixedThreadPool(10);
//
//    private Shop queryWithLogicalExpire(Long id) {
//        // 1 从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //  2判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //  3存在返回缓存数据
//            return null;
//        }
//        // 命中，把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期，返回数据
//            return shop;
//        }
//        // 过期，尝试获取锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        if (isLock) {
//            // 成功，开启异步线程重建缓存
//            CACHE_REBUILD_POOL.submit(() -> {
//                try {
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//
//        //失败，直接返回过期数据
//
//        // 7返回数据
//        return shop;
//    }



//    private Shop queryWithMutex(Long id) {
//        // 1 从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //  2判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //  3存在返回缓存数据
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 是否是空值
//        if (shopJson != null) {
//            return null;
//        }
//        //  实现缓存重建
//        //  获取锁
//        String lockKey = "lock:shop" + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //  判断是否获取到锁
//            if (!isLock) {
//                //  未获取到锁，休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //  4成功，根据id查询数据库
//            shop = getById(id);
//            // 休眠模拟重建延时
//            Thread.sleep(200);
//            // 5不存在返回错误
//            if (shop == null) {
//                // 空值返回redis缓存
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 6存在，写入redis缓存
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放互斥锁
//            unlock(lockKey);
//        }
//        // 7返回数据
//        return shop;
//    }

    //缓存穿透
//    private Shop queryWithPassThrough(Long id) {
//        // 1 从redis查询商铺缓存
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //  2判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //  3存在返回缓存数据
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        // 是否是空值
//        if (shopJson != null) {
//            return null;
//        }
//        //  4不存在，根据id查询数据库
//        Shop shop = getById(id);
//        // 5不存在返回错误
//        if (shop == null) {
//            // 空值返回redis缓存
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 6存在，写入redis缓存
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 设置过期时间
//
//        // 7返回数据
//        return shop;
//    }

//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id, Long expireTimeSeconds) throws InterruptedException {
//        // 1 从数据库查询商铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        // 封装逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTimeSeconds));
//        // 2 写入redis缓存
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("商铺id不能为空");
        }
        // 1 更新数据库
        updateById(shop);
        // 2 删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. 判断是否需要根据坐标查询
        if (x == null || y == null){
            // 2. 不需要，直接查询
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 从redis查询按距离排序，分页
        String key = SHOP_GEO_KEY + typeId;
        // 查询附近的商铺 GEORADIUS KEY x y
        Circle within = new Circle(new Point(x, y), new Distance(5, Metrics.KILOMETERS));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(key, within, RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));

        // 解析id
        if (results ==null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from){
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        System.out.println(ids);
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 获取id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 根据id查询商铺信息
        String idsStr = StrUtil.join(",", ids);
        // 根据id查询店铺，但是要注意顺序，就按照ids的顺序
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id, " + idsStr + ")").list();
        for (Shop shop: shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
