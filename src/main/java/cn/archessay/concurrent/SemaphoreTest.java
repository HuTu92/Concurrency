package cn.archessay.concurrent;

import java.util.concurrent.Semaphore;

public class SemaphoreTest {

    private static Semaphore sem = new Semaphore(0);

    private static class Thread1 extends Thread {
        @Override
        public void run() {
            sem.acquireUninterruptibly();
        }
    }

    private static class Thread2 extends Thread {
        @Override
        public void run() {
            sem.release();
        }
    }

    public static void main(String[] args) throws InterruptedException {

        for (int i = 0; i < 10000000; i++) {
            Thread t1 = new Thread1(); // 获取
            Thread t2 = new Thread1(); // 获取
            Thread t3 = new Thread2(); // 释放
            Thread t4 = new Thread2(); // 释放

            t1.setName("thread1");
            t2.setName("thread2");
            t3.setName("thread3");
            t4.setName("thread4");

            t1.start();
//            TimeUnit.SECONDS.sleep(3); // 确保t1执行完入队逻辑并park后，再执行t2
            t2.start();
//            TimeUnit.SECONDS.sleep(60);
            t3.start();
            t4.start();

            t1.join();
            t2.join();
            t3.join();
            t4.join();

            System.out.println(i);
        }
    }

}
