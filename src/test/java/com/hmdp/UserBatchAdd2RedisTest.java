package com.hmdp;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/21 20:40
 * @comment
 */
@SpringBootTest
public class UserBatchAdd2RedisTest {
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void testBatchAdd() throws IOException {
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
        }

        fileWriter.close();
    }
}
