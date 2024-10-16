package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/16 14:45
 * @comment
 */
public class SimpleRedisLock implements ILock {

    private String name;  // 业务标识
    private StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        String key = KEY_PREFIX + name;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        // 避免自动拆箱时的空指针风险
        return Boolean.TRUE.equals(success);
    }

    // 将脚本作为static加载到内存中 不用每次释放锁都加载脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public void unLock() {
        // 调用Lua脚本
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                List.of(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

/*    @Override
    public void unLock() {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 只能释放自己的锁
        if (threadId.equals(id)) {
            // 释放锁
            String key = KEY_PREFIX + name;
            redisTemplate.delete(key);
        }
    }*/
}
