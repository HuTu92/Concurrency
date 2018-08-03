package com.github.hutu92;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by 刘春龙 on 2018/8/3.
 */
public class Interrupted {

    static class Run implements Runnable {

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

    static class Wait implements Runnable {

        @Override
        public void run() {
            try {
                try {
                    while (true) {
                        TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
                        System.out.println("Wait 线程被唤醒后！~");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.out.println("这是 Wait 线程抛出的中断异常！~");
                }
            } finally {
                System.out.println("Wait 线程停止！~");
            }
        }
    }

    static class Park implements Runnable {

        @Override
        public void run() {
            try {
//                while (true) {
                    LockSupport.park(this);
                    System.out.println("Park 线程被唤醒后！~");
//                }
            } finally {
                System.out.println("Park 线程停止！~");
            }
        }
    }

    /*

        知识点：

            1. 如果是因为 wait 或 sleep 或 join 操作而阻塞，那么会被中断，中断标记会被清除，同时抛出InterruptedException。
            其实就两种情况，wait/sleep，因为join内部也是调用的wait

            2. LockSupport.park();中断不会抛出InterruptedException。
     */

    /*

        运行结果1：

                Run 正在运行！~
                Park 线程被唤醒后！~
                Park 线程停止！~
                Run interrupted is true
                Park interrupted is true
                Wait interrupted is false
                这是 Wait 线程抛出的中断异常！~
                Wait 线程停止！~
                java.lang.InterruptedException: sleep interrupted
                    at java.lang.Thread.sleep(Native Method)
                    at java.lang.Thread.sleep(Thread.java:340)
                    at java.util.concurrent.TimeUnit.sleep(TimeUnit.java:386)
                    at com.github.hutu92.Interrupted$Wait.run(Interrupted.java:36)
                    at java.lang.Thread.run(Thread.java:745)
                Run 正在运行！~
                Run 正在运行！~
                Run 正在运行！~
                Run 正在运行！~

        运行结果2：

                Run 正在运行！~
                java.lang.InterruptedException: sleep interrupted
                    at java.lang.Thread.sleep(Native Method)
                    at java.lang.Thread.sleep(Thread.java:340)
                    at java.util.concurrent.TimeUnit.sleep(TimeUnit.java:386)
                    at com.github.hutu92.Interrupted$Wait.run(Interrupted.java:36)
                    at java.lang.Thread.run(Thread.java:745)
                Park 线程被唤醒后！~
                Park 线程停止！~
                Run interrupted is true
                Park interrupted is false
                Wait interrupted is false
                这是 Wait 线程抛出的中断异常！~
                Wait 线程停止！~
                Run 正在运行！~
                Run 正在运行！~

        TODO 1. Park interrupted is true / false ?
        TODO 2. 如果去掉Park线程中while循环的注释，Park线程后续的LockSupport.park()将不会再等待（无效），将一直循环输出out ?
     */

    public static void main(String[] args) throws InterruptedException {

        Thread run = new Thread(new Run());
        Thread wait = new Thread(new Wait());
        Thread park = new Thread(new Park());

        run.start();
        wait.start();
        park.start();
        TimeUnit.SECONDS.sleep(2);

        run.interrupt();
        wait.interrupt();
        park.interrupt();

        System.out.println("Run interrupted is " + run.isInterrupted());
        System.out.println("Park interrupted is " + park.isInterrupted());
        System.out.println("Wait interrupted is " + wait.isInterrupted());

        TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    }
}