package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.log.Log;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        // 存在缓存穿透的查询
        // Shop shop = queryWithPassThrough(id);

        // 使用互斥锁解决缓存穿透
        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        // 数据存在于缓存
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (shopJson != null && !shopJson.isEmpty()) {
            Shop shop = JSONObject.parseObject(shopJson, Shop.class);
            return shop;
        }

        // 判断是否是存入的空对象
        if (shopJson != null) {
            // 此时 shopJson 为空串 ""
            return null;
        }

        // 数据不存在于缓存 就涉及到缓存重建
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                log.info("get lock failed...");
                // 获取锁失败 休眠并重试查询
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 获取锁成功 执行查询
            log.info("get lock success...");
            shop = getById(id);
            Thread.sleep(200); // 模拟长时间重建
            // 数据不存在于数据库 返回错误
            if (shop == null) {
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 数据存在于数据库 重建数据
            shopJson = JSON.toJSONString(shop);
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            log.info("release lock");
            unlock(lockKey);
        }

        return shop;
    }


    public Shop queryWithPassThrough(Long id){
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (shopJson != null && !shopJson.isEmpty()) {
            Shop shop = JSONObject.parseObject(shopJson, Shop.class);
            return shop;
        }

        // 判断是否是存入的空对象
        if (shopJson != null) {
            // 此时 shopJson 为空串 ""
            return null;
        }

        // NOT existed in DB
        Shop shop = getById(id);
        if (shop == null) {
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // NOT existed in Redis
        // BUT existed in DB
        shopJson = JSON.toJSONString(shop);
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // update DB Then delete CACHE
        updateById(shop);
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }
}
