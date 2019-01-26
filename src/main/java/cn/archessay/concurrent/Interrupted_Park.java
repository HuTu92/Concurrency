package cn.archessay.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by 刘春龙 on 2018/8/3.
 */
public class Interrupted_Park {

    static class Wait implements Runnable {

        @Override
        public void run() {
            try {
                long i = 0;
                while (true) {
                    i++;
                    System.out.println("这是 Wait 线程第 " + i + " 次循环！～");
                    try {
                        TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
                        if (i % 100000000 == 0) {
                            System.out.println("Wait 线程被唤醒后！~");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.out.println("这是 Wait 线程抛出的中断异常！~");
                    }
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
                long i = 0;
                while (true) {
                    i++;
                    LockSupport.park(this);
                    if (i % 100000000 == 0) {
                        System.out.println("Park 线程被唤醒后！~");
                    }
                }
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
        QA 如果中断了park线程，park线程会继续执行while循环，然后执行内部的LockSupport.park(this);System.out...
        但是此时的park并不会再次进入等待状态，而是直接执行System.out的代码。为什么？

            Java中如果一个线程被中断了，线程继续执行的话，有两种情况：

            1. 如果是线程继续执行的过程中再次遇到sleep，会继续进入wait状态等待。
            2. 如果是线程继续执行的过程中再次遇到park，此时线程不会进入等待状态，而是继续执行。

            这也是sleep和park的区别。

            具体什么原因，得看park底层实现。

            参见：https://mp.weixin.qq.com/s?__biz=MzU4MDY3ODg3OA==&mid=2247483752&idx=1&sn=ca0843e32b3223d7ac9422597b0ad9b8&chksm=fd5264d9ca25edcfb7bf7ecbecc2e198312535ea7ef1540e671d24508fa89143989c63490e25&token=1487648090&lang=zh_CN#rd

     */

    public static void main(String[] args) throws InterruptedException {

        Thread wait = new Thread(new Wait());
        Thread park = new Thread(new Park());

        wait.start();
        park.start();
        TimeUnit.SECONDS.sleep(2);

        wait.interrupt();
        park.interrupt();

        System.out.println("Park interrupted is " + park.isInterrupted());
        System.out.println("Wait interrupted is " + wait.isInterrupted());

        TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    }
}