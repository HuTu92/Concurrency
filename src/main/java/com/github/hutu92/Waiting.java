package com.github.hutu92;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by 刘春龙 on 2018/8/3.
 * <p>
 * 检验Sleep与Wait会不会释放锁
 */
public class Waiting {

    static AtomicInteger s = new AtomicInteger();
    static AtomicInteger w = new AtomicInteger();
    static AtomicInteger p = new AtomicInteger();


    static class Sleep implements Runnable {

        @Override
        public void run() {

            synchronized (s) {
                try {
                    System.out.println("Sleep " + s.incrementAndGet() + " 线程获得锁");
                    TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    static class Wait implements Runnable {

        @Override
        public void run() {

            synchronized (w) {
                try {
                    System.out.println("Wait " + w.incrementAndGet() + " 线程获得锁");
                    w.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    static class Park implements Runnable {

        @Override
        public void run() {

            synchronized (p) {
                System.out.println("Park " + p.incrementAndGet() + " 线程获得锁");
                LockSupport.park();
            }

        }
    }

    /*

        输出结果：

            Sleep 1 线程获得锁
            Wait 1 线程获得锁
            Park 1 线程获得锁
            Wait 2 线程获得锁
            Wait 3 线程获得锁
            Wait 4 线程获得锁
            Wait 5 线程获得锁


        可知，wait会释放线程占有得锁，而sleep、park则不会。

     */
    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread sleep1 = new Thread(new Sleep(), "sleep");
            Thread wait1 = new Thread(new Wait(), "wait");
            Thread park1 = new Thread(new Park(), "park");

            sleep1.start();
            wait1.start();
            park1.start();

            TimeUnit.SECONDS.sleep(5);

            Thread sleep2 = new Thread(new Sleep(), "sleep");
            Thread wait2 = new Thread(new Wait(), "wait");
            Thread park2 = new Thread(new Park(), "park");

            sleep2.start();
            wait2.start();
            park2.start();
        }
    }
}
