package com.github.hutu92.lock;

import java.util.concurrent.TimeUnit;

/**
 * Created by 刘春龙 on 2018/8/3.
 */
public class WaitAndSleep {


    static class Sleep implements Runnable {

        @Override
        public void run() {

            try {
                TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }
}
