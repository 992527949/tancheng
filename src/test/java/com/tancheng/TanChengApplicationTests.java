package com.tancheng;

import com.tancheng.entity.Shop;
import com.tancheng.service.impl.ShopServiceImpl;
import com.tancheng.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class TanChengApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

//    @Test
//    void testSaveShop() throws InterruptedException {
//        shopService.saveShop2Redis(1L, 10L);
//    }


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Test
    void loadShopData(){
        // 1. 从数据库中加载店铺数据
        List<Shop> list = shopService.list();
        // 2. 把店铺分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3. 把店铺数据保存到redis中
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            List<Shop> values = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(values.size());
            for(Shop shop : values){
                locations.add(new RedisGeoCommands.GeoLocation<>
                        (shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testNextId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("耗时：" + (end - begin));
    }
}
