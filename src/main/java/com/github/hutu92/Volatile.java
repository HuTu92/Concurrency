package com.github.hutu92;

/**
 * Created by liuchunlong on 2018/7/25.
 */
public class Volatile {

    int i = 0;
    volatile boolean flag = false;

    //Thread A
    public void write() {   // 1, 2不会重排序，volatile后面会插入StoreLoad全能型屏障
        flag = true;    // 1
        i = 2;          // 2
    }

    //Thread B
    public void read() {
        if (i == 2) {                      // 4
            System.out.println(flag);      // 3
        }
    }
}
