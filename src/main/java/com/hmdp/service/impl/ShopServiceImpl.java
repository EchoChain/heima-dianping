package com.hmdp.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        // existed in Redis
        String shopStr = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (shopStr != null) {
            Shop shop = JSONObject.parseObject(shopStr, Shop.class);
            return Result.ok(shop);
        }

        // NOT existed in Redis
        // BUT existed in DB
        Shop shop = getById(id);
        if (shop != null) {
            shopStr = JSON.toJSONString(shop);
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopStr);
            return Result.ok(shop);
        }

        // NOT existed in DB
        return Result.fail("ShopId NOT EXISTED in DB");
    }
}
