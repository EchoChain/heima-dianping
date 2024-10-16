package com.hmdp.service.impl;

import com.alibaba.fastjson2.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result listWithCache() {
        List<ShopType> list = new ArrayList<>();

        // 从Redis缓存中查询ShopType数据
        if (Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_SHOPTYPE_KEY))) {
            Set<String> set = redisTemplate.opsForZSet().range(CACHE_SHOPTYPE_KEY, 0, -1);
            if (set != null) {
                list = set.stream()
                        .map(shopTypeStr -> JSON.parseObject(shopTypeStr, ShopType.class))
                        .toList();
                return Result.ok(list);
            }
        }

        // 缓存中不存在 在数据库中查询ShopType数据
        list = query().orderByAsc("sort").list();

        // 数据库中没查询到ShopType 返回fail
        if (list.isEmpty()) {
            return Result.fail("店铺列表为空!");
        }

        // 数据库中查询到ShopType
        HashSet<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        list.forEach(shopType -> tuples.add(new DefaultTypedTuple<>(
                JSON.toJSONString(shopType),
                shopType.getSort() * 1.0
        )));

        redisTemplate.opsForZSet().add(CACHE_SHOPTYPE_KEY, tuples);  // 将查到的数据存入Redis
        return Result.ok(list);  // 返回查到的数据
    }
}
