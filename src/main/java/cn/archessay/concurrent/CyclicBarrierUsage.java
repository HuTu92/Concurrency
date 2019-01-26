package cn.archessay.concurrent;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Created by liuchunlong on 2018/8/18.
 */
public class CyclicBarrierUsage {
    private static CyclicBarrier cyclicBarrier = new CyclicBarrier(2, new Third());

    static class First implements Runnable {

        @Override
        public void run() {
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }

            int i = 0;
            do {
                i++;
            } while (i < 100);

            System.out.println("1");
        }
    }

    /*
        3
        2
        1

        或者

        3
        1
        2
     */

    /**
     * 之所以会有两种结果，因为主线程和First线程的执行顺序和CPU调度有关。
     */
    public static void main(String[] args) {
        Thread first = new Thread(new First(), "first");
        first.start();

        try {
            cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
        System.out.println("2");
    }

    /**
     * 此时，First和Third线程都不会执行，因为CyclicBarrier设置拦截线程的数量为2，
     * 这里只有First执行了await，所以First和Third线程都不会执行，只有其它线程也执行了await后，
     * Third线程才会先执行，而后执行First和其它线程。
     */
//    public static void main(String[] args) {
//        Thread first = new Thread(new First(), "first");
//        first.start();
//    }


    static class Third implements Runnable {

        @Override
        public void run() {
            System.out.println("3");
        }
    }
}
