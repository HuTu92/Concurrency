package com.github.hutu92;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Created by liuchunlong on 2018/8/18.
 */
public class CyclicBarrierInterrupt {

    private static CyclicBarrier cyclicBarrier = new CyclicBarrier(2);

    static class First implements Runnable {

        @Override
        public void run() {
            try {
                cyclicBarrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println("1");
        }
    }

    public static void main(String[] args) {
        Thread first = new Thread(new First());
        first.start();

        first.interrupt();

        try {
            cyclicBarrier.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            System.out.println(cyclicBarrier.isBroken());
            e.printStackTrace();
        }
    }
}
