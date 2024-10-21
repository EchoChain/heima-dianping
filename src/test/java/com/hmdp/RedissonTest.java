package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/16 17:37
 * @comment
 */
@SpringBootTest
@Slf4j
public class RedissonTest {
    @Autowired
    private RedissonClient redissonClient;

    @Test
    void method1() throws InterruptedException {
        RLock lock = redissonClient.getLock("lock");
        boolean isLock = lock.tryLock(1L, TimeUnit.MINUTES);
        if (!isLock) {
            log.error("获取锁失败, 1");
            return;
        }
        try {
            log.info("获取锁成功, 1");
            method2();
        } finally {
            log.info("释放锁, 1");
            lock.unlock();
        }
    }


    void method2() {
        RLock lock = redissonClient.getLock("lock");
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败, 2");
            return;
        }
        try {
            log.info("获取锁成功, 2");
        } finally {
            log.info("释放锁, 2");
            lock.unlock();
        }
    }
}
