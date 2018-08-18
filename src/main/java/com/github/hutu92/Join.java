package com.github.hutu92;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Created by liuchunlong on 2018/8/18.
 */
public class Join {

    static class MasterThread implements Runnable {

        @Override
        public void run() {
            System.out.println("Thread Master run start!~");
            try {
                System.out.println("Thread Master sleep 10s");
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Thread Master run end!~");
        }
    }

    static class Slave1Thread implements Runnable {

        private Thread master;

        public Slave1Thread(Thread master) {
            this.master = master;
        }

        @Override
        public void run() {
            System.out.println("Thread Slave1 run start!~");
            try {
                master.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Thread Slave1 run end!~");
        }
    }

    static class Slave2Thread implements Runnable {

        private Thread master;

        public Slave2Thread(Thread master) {
            this.master = master;
        }

        @Override
        public void run() {
            System.out.println("Thread Slave2 run start!~");
            try {
                master.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Thread Slave2 run end!~");
        }
    }

    /*

        Thread Slave1 run start!~
        Thread Slave1 run end!~
        Thread Slave2 run start!~
        Thread Slave2 run end!~
        Thread main Sleep 5s
        Thread Master run start!~
        Thread Master sleep 10s
        Thread Master run end!~

     */

    /**
     * slave1 & slave2 先执行，在其调用master.join()时判断master线程是否存活（isAlive()）存活则进入等待状态，直到master执行结束，
     * 因为master线程还未启动，所以isAlive()为false，不会进入等待状态。
     *
     * @throws InterruptedException
     */
    @Test
    public void testJoinBefore() throws InterruptedException {
        Thread master = new Thread(new MasterThread(), "master");

        Thread slave1 = new Thread(new Slave1Thread(master), "slave1");
        Thread slave2 = new Thread(new Slave2Thread(master), "slave2");

        slave1.run();
        slave2.run();

        System.out.println("Thread main Sleep 5s");
        TimeUnit.SECONDS.sleep(5);

        master.run();
    }

    /*
        Thread Master run start!~
        Thread Master sleep 10s
        Thread Master run end!~
        Thread Slave1 run start!~
        Thread Slave1 run end!~
        Thread Slave2 run start!~
        Thread Slave2 run end!~
     */

    /**
     * 这里主要是为了说明 com.github.hutu92.code.analysis.Join 中提到的 notifyAll()。
     *
     * 因为master会被多个线程join从而进入等待状态，所以需要使用notifyAll而不是notify来唤醒所有的这些线程。
     *
     * @throws InterruptedException
     */
    @Test
    public void testJoinAfter() throws InterruptedException {
        Thread master = new Thread(new MasterThread(), "master");

        Thread slave1 = new Thread(new Slave1Thread(master), "slave1");
        Thread slave2 = new Thread(new Slave2Thread(master), "slave2");

        master.run();

        slave1.run();
        slave2.run();
    }
}
