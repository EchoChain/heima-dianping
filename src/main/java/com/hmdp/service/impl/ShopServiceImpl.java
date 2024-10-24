package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

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
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        Shop shop;
        // 使用缓存空对象解决缓存穿透
        // shop = queryWithPassThrough(id);

        // 使用互斥锁解决缓存击穿
        // shop = queryWithMutex(id);

        // 使用逻辑过期解决缓存击穿
        // shop = queryWithLogicExpire(id);

        // 使用工具类解决缓存穿透
        shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_NULL_TTL, TimeUnit.SECONDS);

        // 使用工具类解决缓存击穿
        // shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

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

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicExpire(Long id) {
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 查缓存 如果未命中 返回null
        if (shopJson == null || shopJson.isEmpty()) {
            return null;
        }

        // 如果命中 判断是否过期
        RedisData redisData = JSONObject.parseObject(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = data.to(Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();


        // 未过期 返回shop
        if (LocalDateTime.now().isBefore(expireTime)) {
            return shop;
        }

        // 已过期
        // 尝试获取锁
        String key = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(key);
        if (isLock) {
            // 要再次检查Redis缓存是否过期
            // 可能在当前线程等待锁的过程中 Redis缓存已经被重建 此处先不写了

            // 从线程池拉一个新线程重建数据 返回旧的shop
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    // 重建缓存
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(key);
                }
            });

        }

        return shop;
    }


    public Shop queryWithPassThrough(Long id) {
        // 在缓存中查找
        String shopJson = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (shopJson != null && !shopJson.isEmpty()) {
            Shop shop = JSONObject.parseObject(shopJson, Shop.class);
            return shop;
        }

        // 判断缓存中是否是存入的空对象
        if (shopJson != null) {
            // 此时 shopJson 为空串 ""
            return null;
        }

        // 在数据库查找
        Shop shop = getById(id);

        // 数据库中不存在 缓存null 返回null
        if (shop == null) {
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 数据库中存在 缓存shop 返回shop
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("typeId", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 计算分页参数
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;

        // 查询redis 按照距离排序 分页 结果: shopId distance
        String key = SHOP_GEO_KEY + typeId;
        Circle circle = new Circle(new Point(x, y), new Distance(5000));
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .radius(
                        key,
                        circle,
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
                );

        // 解析出id
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() < from) {
            return Result.ok(Collections.emptyList());
        }
        // 1.截取 from - end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result->{
            // 2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 返回
        return Result.ok(shops);
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 准备热点数据 redisData
        RedisData redisData = new RedisData();
        Shop shop = getById(id);
        Thread.sleep(200); // 模拟延迟
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 热点数据存入 Redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSON.toJSONString(redisData));
    }
}
