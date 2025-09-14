package com.tancheng.utils;


//import com.sun.xml.internal.ws.resources.UtilMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /*
    * 时间戳
    * */
    private static final long BEGIN_TIMESTAMP = 1609430400L;
    /**
     * 序列号长度32位
     */
    private static final int SEQUENCE_BIT = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 获取日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":"+date);
        // 3.拼接
        return timestamp << SEQUENCE_BIT | count;
    }


}
