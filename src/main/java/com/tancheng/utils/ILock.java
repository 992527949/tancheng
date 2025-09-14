package com.tancheng.utils;

public interface ILock {
    boolean tryLock(long timeOutSec);

    void unlock();
}
