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

                long i = 0;
                while (true) {
                    if (i % 1000000000 == 0) {
                        System.out.println("Park 正在运行！~");
                    }
                    i++;
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

        QA Park interrupted is true / false ?

                如果将Park线程中的59～65行代码注掉，Park线程会因为中断跳出等待状态，然后执行完finally代码进而结束线程，
                从而将中断标识位清除。

                如果Park线程因为中断跳出等待状态，继续运行59～65行代码，现在永远不会结束，从而不会清除中断标记。
     */

    public static void main(String[] args) throws InterruptedException {

        int count = 0;

        while (true) {
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

            boolean parkIsInterrupted = false;
            System.out.println("Run interrupted is " + run.isInterrupted());
            System.out.println("Park interrupted is " + (parkIsInterrupted = park.isInterrupted()));
            System.out.println("Wait interrupted is " + wait.isInterrupted());

            if (!parkIsInterrupted) {
                System.out.println("出现预期结果！～");
                System.exit(0);
            }

            count++;

            System.out.println("第 " + count + " 次循环测试！～");
        }
    }
}