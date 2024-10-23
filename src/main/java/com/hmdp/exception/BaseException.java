package com.hmdp.exception;

/**
 * @author Cheng Yihao
 * @version 1.0
 * @date 2024/10/22 22:58
 * @comment
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    public BaseException(String msg) {
        super(msg);
    }

}

