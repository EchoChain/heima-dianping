package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/16 14:45
 * @comment
 */
public class SimpleRedisLock implements ILock{

    private String name;  // 业务标识
    private StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取线程标识
        long threadId = Thread.currentThread().getId();
        // 获取锁
        String key = KEY_PREFIX + name;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(threadId), timeoutSec, TimeUnit.SECONDS);
        // 避免自动拆箱时的空指针风险
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        // 释放锁
        String key = KEY_PREFIX + name;
        redisTemplate.delete(key);
    }
}
