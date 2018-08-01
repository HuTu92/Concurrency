package com.github.hutu92.pool;

/**
 * Created by liuchunlong on 2018/7/29.
 *
 * 线程池接口
 */
public interface ThreadPool<Job extends Runnable> {

    // 执行一个Job，这个Job需要实现Runnable
    void execute(Job job);

    // 关闭线程池
    void shutdown();

    // 增加工作者线程
    void addWorkers(int num);

    // 减少工作者线程
    void removeWorkers(int num);

    // 得到正在执行的任务数量
    int getJobSize();
}
