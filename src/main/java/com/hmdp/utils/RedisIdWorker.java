package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/15 16:29
 * @comment
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1009843200L;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String preFix) {
        LocalDateTime now = LocalDateTime.now();

        // 1.生成时间戳
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = redisTemplate.opsForValue().increment("inr:" + preFix + ":" + date);

        // 3.拼接并返回
        return timestamp << 32 | count;
    }
}
