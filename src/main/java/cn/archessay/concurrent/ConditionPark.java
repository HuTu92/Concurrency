package cn.archessay.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by 刘春龙 on 2018/8/9.
 *
 * 「测试目的」：测试A线程的con.await()中调用的LockSupport.park(this)是被什么操作唤醒的？
 *
 * 「测试方式」：先打断点，如图images/ConditionPark.png；然后运行程序
 *
 * 「测试结果」：B线程执行con.signal()并不会触发断点，5秒后，执行lock.unlock()触发断点
 *
 * 「测试结论」：其它线程调用.signal()并不会唤醒.await()中的LockSupport.park(this)，
 *              只有调用.unlock()释放锁后才会去唤醒
 */
public class ConditionPark {

    static Lock lock = new ReentrantLock();
    static Condition con = lock.newCondition();

    static class A implements Runnable {

        @Override
        public void run() {

            try {
                lock.lock();

                try {
                    con.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } finally {
                lock.unlock();
            }
            System.out.println(Thread.currentThread().getName() + " 线程执行结束！~");
        }
    }

    static class B implements Runnable {

        private Thread thread;

        public B(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            try {
                lock.lock();

                con.signal();
                System.out.println(Thread.currentThread().getName() + " 线程signal通知 " + thread.getName() + " 线程！~");
            } finally {

                System.out.println(Thread.currentThread().getName() + " 线程5秒后释放锁！~");

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                lock.unlock();
            }
            System.out.println(Thread.currentThread().getName() + " 线程执行结束！~");
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
