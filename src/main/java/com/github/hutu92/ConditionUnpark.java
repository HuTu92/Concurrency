package com.github.hutu92;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by liuchunlong on 2018/8/7.
 *
 * 该示例表明，A线程正常执行，其它线程对其unpark不会产生任何影响，A还是正常执行
 */
public class ConditionUnpark {

    static class A implements Runnable {

        @Override
        public void run() {
            try {
                long i = 0;
                while (true) {
                    if (i % 1000000000 == 0) {
                        System.out.println("Run 正在运行！~");
                    }
                    i++;
                }
            } finally {
                System.out.println("Run 线程停止！~");
            }
        }
    }

    static class B implements Runnable {

        private Thread thread;

        public B(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            LockSupport.unpark(thread);
            System.out.println(Thread.currentThread().getName() + " 唤醒线程 " + thread.getName());
        }
    }

    public static void main(String[] args) throws InterruptedException {

        Thread a = new Thread(new A(), "a");
        a.start();

        TimeUnit.SECONDS.sleep(5);

        Thread b = new Thread(new B(a), "b");
        b.start();
    }
}
