package com.github.hutu92;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;

/**
 * Created by 刘春龙 on 2018/8/17.
 */
public class ForkJoin {

    private static final int THRESHOLD = 2; // 阈值

    static class CountTask extends RecursiveTask<Integer> {

        private int start;
        private int end;

        public CountTask(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected Integer compute() {

            int sum = 0;

            // 如果任务足够小，直接计算任务，不再拆分
            boolean canCompute = (end - start) <= THRESHOLD;
            if (canCompute) {
                for (int i = start; i <= end; i++) {
                    sum += i;
                }
            } else { // 继续拆分任务
                // 如果任务大于阈值，就分裂成两个子任务计算
                int middle = (start + end) / 2;

                CountTask leftTask = new CountTask(start, middle);
                CountTask rightTask = new CountTask(middle + 1, end);

                leftTask.fork();
                rightTask.fork();

                int leftResult = leftTask.join();
                int rightResult = rightTask.join();

                sum = leftResult + rightResult;
            }

            return sum;
        }
    }

    public static void main(String[] args) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        CountTask countTask = new CountTask(1, 4);
        ForkJoinTask<Integer> forkJoinTask = forkJoinPool.submit(countTask);
        try {
            System.out.println(forkJoinTask.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
