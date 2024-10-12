package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/12 14:53
 * @comment
 */
@Component
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        log.info("RefreshTokenInterceptor is Working...");

        String token = request.getHeader("authorization");
        if (token != null) {
            String key = LOGIN_USER_KEY + token;
            Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
            if (!userMap.isEmpty()) {
                UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                UserHolder.saveUser(userDTO);
                redisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
        }



        return true;
    }
}
