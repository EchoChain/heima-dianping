package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/16 17:03
 * @comment
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redisClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
