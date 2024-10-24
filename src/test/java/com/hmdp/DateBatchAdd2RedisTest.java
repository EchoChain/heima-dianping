package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/21 20:40
 * @comment
 */
@SpringBootTest
public class DateBatchAdd2RedisTest {
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IShopService shopService;

    @Test
    void loadUser() throws IOException {
        FileWriter fileWriter = new FileWriter("tokens.txt");

        List<User> list = userService.query().list();
        for (User user : list) {
            String phone = user.getPhone();
            // 获取验证码
            userService.sendCode(phone);
            String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            // 登录
            LoginFormDTO loginFormDTO = LoginFormDTO.builder()
                    .phone(phone)
                    .code(code)
                    .build();
            String token = (String) userService.login(loginFormDTO).getData();
            // 写入token
            fileWriter.write(token);
            fileWriter.write("\r\n");
            fileWriter.flush();
        }

        fileWriter.close();
    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            redisTemplate.opsForGeo().add(key, locations);
        }
    }
}
