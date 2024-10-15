package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/13 15:59
 * @comment
 */
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSON.toJSONString(value), time, unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入 Redis
        redisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));
    }

    public <ID, R> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 在缓存中查找
        // 存在 直接返回
        String key = keyPrefix + id;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            return JSONObject.parseObject(json, type);
        }


        // 判断缓存中是否是存入的空对象
        if (json != null) {
            // 此时 json 为空串 ""
            return null;
        }

        // 在数据库查找
        // 改造成函数式接口
        R r = dbFallback.apply(id);

        // 数据库中不存在 缓存null 返回null
        if (r == null) {
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 数据库中存在 缓存r 返回r
        this.set(key, r, time, unit);
        return r;
    }

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicExpire(String preFix ,ID id, Class<R> type,
                                          Function<ID, R> dbFallBeck, Long time, TimeUnit unit) {
        String key = preFix + id;

        String json = redisTemplate.opsForValue().get(key);

        // 查缓存 如果未命中 返回null
        if (json == null || json.isEmpty()) {
            return null;
        }

        // 如果命中 判断是否过期
        RedisData redisData = JSONObject.parseObject(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = ((JSONObject) redisData.getData()).to(type);


        // 未过期 返回shop
        if (LocalDateTime.now().isBefore(expireTime)) {
            return r;
        }

        // 已过期
        // 尝试获取锁
        boolean isLock = tryLock(key);
        if (isLock) {
            // 要再次检查Redis缓存是否过期
            // 可能在当前线程等待锁的过程中 Redis缓存已经被重建 此处先不写了

            // 从线程池拉一个新线程重建数据 返回旧的shop
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    // 重建缓存
                    // 先查数据库
                    R r_DB = dbFallBeck.apply(id);
                    // 再存入缓存
                    this.setWithLogicExpire(key, r_DB, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(key);
                }
            });
        }

        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }
}
