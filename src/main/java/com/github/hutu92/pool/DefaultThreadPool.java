package com.github.hutu92.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by liuchunlong on 2018/7/29.
 */
public class DefaultThreadPool<Job extends Runnable> implements ThreadPool<Job> {

    // 线程池最大限制数
    private static final int MAX_WORKER_NUMBERS = 10;

    // 线程池默认的数量
    private static final int DEFAULT_WORKER_NUMBERS = 5;

    // 线程池最小的数量
    private static final int MIN_WORKER_NUMBERS = 1;



    // 这是一个工作（任务）列表，将会向里面插入工作
    private final LinkedList<Job> jobs = new LinkedList<Job>();

    // 工作者列表
    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<Worker>());

    // 工作者线程的数量
    private int workerNum = DEFAULT_WORKER_NUMBERS;

    // 线程编号生成
    private AtomicLong threadNum = new AtomicLong();

    public DefaultThreadPool() {
        initializeWorkers(workerNum);
    }

    public DefaultThreadPool(int num) {
        workerNum = num > MAX_WORKER_NUMBERS ? MAX_WORKER_NUMBERS : num < MIN_WORKER_NUMBERS ? MIN_WORKER_NUMBERS : num;
        initializeWorkers(workerNum);
    }

    // 初始化线程工作者
    private void initializeWorkers(int num) {
        for (int i = 0; i < num; i++) {
            Worker worker = new Worker();
            workers.add(worker);
            Thread thread = new Thread(worker, "ThreadPool-Worker-" + threadNum.incrementAndGet());
            thread.start();
        }
    }

    public void execute(Job job) {
        if (job != null) {
            // 添加一个任务，然后通知
            synchronized (jobs) {
                jobs.addLast(job);
                jobs.notify();
            }
        }
    }

    public void shutdown() {
        for (Worker worker : workers) {
            worker.shutdown();
        }
    }

    public void addWorkers(int num) {
        synchronized (jobs) {
            // 限制新增的Worker数量不能超过最大值
            if (num + this.workerNum > MAX_WORKER_NUMBERS) {
                num = MAX_WORKER_NUMBERS - this.workerNum;
            }
            initializeWorkers(num);
            this.workerNum = num;
        }
    }

    public void removeWorkers(int num) {
        synchronized (jobs) {
            if (num >= this.workerNum) {
                throw new IllegalArgumentException("beyond workNum");
            }

            // 按照给定的数量停止Worker
            int count = 0;
            while (count < num) {
                Worker worker = workers.get(count);
                if (workers.remove(worker)) {
                    worker.shutdown();
                    count++;
                }
            }
            this.workerNum -= count;
        }
    }

    public int getJobSize() {
        return jobs.size();
    }

    // 工作者，负责消费任务
    class Worker implements Runnable {

        // 是否工作
        // 其它线程通过shutdown方法更改该标记为false，以停止当前线程。
        // volatile同步该标记位
        private volatile boolean running = true;

        public void run() {

            while (running) {
                Job job = null;
                synchronized (jobs) {
                    // 如果任务列表是空的，那么就wait
                    while (jobs.isEmpty()) {
                        try {
                            jobs.wait();
                        } catch (InterruptedException e) {
                            // 感知到外部对Worker Thread的终端操作，返回
                            // 抛出InterruptedException异常，会清除中断标记位，这里重新设置中断标记
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    // 取出一个job
                    job = jobs.removeFirst();
                }

                if (job != null) {
                    try {
                        job.run();
                    } catch (Exception e) {
                        // 忽略Job执行的Exception，不关心业务级异常
                    }
                }
            }

        }

        public void shutdown() {
            running = false;
        }
    }
}
