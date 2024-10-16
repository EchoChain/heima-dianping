package com.hmdp.utils;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/16 14:44
 * @comment
 */
public interface ILock {
    /**
     * 获取锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
