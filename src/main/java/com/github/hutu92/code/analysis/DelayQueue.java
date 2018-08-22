package com.github.hutu92.code.analysis;

import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by 刘春龙 on 2018/8/15.
 */
public class DelayQueue {

    /**
     * 重入锁
     */
    private final transient ReentrantLock lock = new ReentrantLock();
    /**
     * 支持优先级的无界队列
     * <p>
     * 请注意，此实现不同步。
     * 如果有任一线程修改队列，则多个线程不应同时访问PriorityQueue实例。
     * 而是使用线程安全的java.util.concurrent.PriorityBlockingQueue类。
     */
    private final PriorityQueue<E> q = new PriorityQueue<E>();

    /**
     *
     */
    private Thread leader = null;

    /**
     * 当队列头部有一个较新的元素可用时，或者一个新线程可能需要成为领导者时，状态就会发出信号。
     */
    private final Condition available = lock.newCondition();

    /**
     * 将指定的元素插入此延迟队列。由于队列是无限制的，因此该方法永远不会阻塞。
     *
     * @param e       the element to add
     * @param timeout This parameter is ignored as the method never blocks
     * @param unit    This parameter is ignored as the method never blocks
     * @return {@code true}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    /**
     * 将指定的元素插入此延迟队列。由于队列是无限制的，因此该方法永远不会阻塞。
     *
     * @param e the element to add
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) {
        offer(e);
    }

    /**
     * 将指定的元素插入此延迟队列。由于队列是无限队列，因此该方法永远不会阻塞。
     *
     * @param e the element to add
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) {
        offer(e);
    }

    /**
     * Inserts the specified element into this delay queue.
     *
     * @param e the element to add
     * @return {@code true}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        // 这里使用的优先级队列PriorityQueue线程不安全，使用ReentrantLock做同步
        final ReentrantLock lock = this.lock;
        // 获取锁
        lock.lock();
        try {
            // 通过 PriorityQueue 来将元素入队
            q.offer(e);
            // peek 是获取的队头元素（但不会从队列中移除该元素），
            // 唤醒阻塞在 available 条件上的一个线程，表示可以从队列中取数据了（但取到的数据也能还未超时）
            if (q.peek() == e) {
                // leader保存最早正在等待获取队首元素的线程
                // q.peek() == e 表示新插入的元素为队首，即更新了队首，
                // 应该清空leader，让线程重新争夺leader
                leader = null;
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////

    /**
     * 检索并删除此队列上具有过期延迟的头部元素，如果此队列没有具有过期延迟的头部元素，则返回null。
     * <p>
     * 注意：该方法不会阻塞，直接返回，没有就返回null。
     *
     * @return 此队列的头部，如果此队列没有具有过期延迟的元素，则返回null
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        // 获取同步锁
        lock.lock();
        try {
            // 获取队头（但不会从队列中移除该元素）
            E first = q.peek();
            if (first == null // 队头为null，即队列中没有元素
                    || first.getDelay(NANOSECONDS) > 0) // 队头的延时还没有到
                // 返回null
                return null;
            else
                return q.poll(); // 队头为过期延迟的元素，出队
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检索并删除此队列上具有过期延迟的头部元素，
     * 必要时等待，直到此队列上具有过期延迟的头部元素或指定的超时等待时间到期为止。
     *
     * @param timeout
     * @param unit
     * @return 此队列的头部，如果等待超时，则返回null
     * @throws InterruptedException
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // 超时等待时间，单位纳秒
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        // 可中断的获取锁
        lock.lockInterruptibly();
        try {
            // 无限循环
            for (; ; ) {
                // 获取队头元素（但不会从队列中移除该元素）
                E first = q.peek();
                // 队头元素为空，也就是队列为空
                if (first == null) {
                    // 等待超时，返回null
                    if (nanos <= 0)
                        return null;
                    else
                        // 还没有超时，那么再在available条件上进行等待nanos时间
                        nanos = available.awaitNanos(nanos);
                } else {
                    // 获取队头元素延迟时间
                    long delay = first.getDelay(NANOSECONDS);
                    // 延时到期
                    if (delay <= 0)
                        return q.poll(); // 队首元素移除队列，并返回该元素
                    // 队首元素延时未到期，超时等待时间到期，返回null
                    if (nanos <= 0)
                        return null;

                    // 到这里，说明队首元素的延迟时间未到期，超时等待时间也未到期

                    first = null; // 等待时不要保留引用

                    // 超时等待时间 < 延迟时间 或者 有其它线程正在等待获取队首元素，那么当前线程进入等待状态。
                    /*
                        1. nanos < delay：当前线程的超时等待时间小于队首元素的延迟时间，不能够处理队首元素，所以直接进入等待状态。
                            可能你会想，既然此时当前线程的超时等待时间小于队首元素的延迟时间（即小于队列中当前的最小延迟时间），
                            那为什么还要继续等待呢？它不可能等待到队首元素超时呀！

                            那是因为在它等待的过程中（当前线程等待会释放锁），队列还有可能会被继续更新（ie，执行offer(E e)），
                            很有可能新增元素的延迟时间就很小（即小于队列中当前的最小延迟时间），该新增元素会被放到队首，然后随机唤醒等待的线程，
                            此时，当前线程就有机会被唤醒。

                        2. leader不为null，表示已有线程正在等待获取队首元素（leader代表最早正在等待获取队首元素的线程），
                            既然已有leader线程正在等待获取队首元素，那么就不再需要当前线程去处理队首元素了，只需进入等待状态即可。
                     */
                    if (nanos < delay || leader != null)
                        // 在 available 条件上进行等待 nanos 时间
                        nanos = available.awaitNanos(nanos);
                    else {
                        // 超时等待时间 > 延迟时间 并且没有其它线程正在等待获取队首元素，那么将当前线程设为leader，表示当前线程最早正在等待获取队首元素
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            // 等待“延迟时间”超时
                            long timeLeft = available.awaitNanos(delay);
                            // 计算当前线程剩余的超时等待时间
                            nanos -= delay - timeLeft;
                        } finally {
                            // 清除 leader
                            // 在finally代码块里清理，是为了防止当前线程的等待状态被中断时，当前线程不能正常释放leader，
                            // 导致因为leader被占用，在最后的finally代码块里不会执行唤醒其它线程
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            // 唤醒阻塞在 available 的一个线程，表示可以取队首元素了
            // 为了防止无效的唤醒，只有当没有其它线程正在等待获取队首元素（leader == null），并且队列中有元素时才会随机唤醒一个线程
            if (leader == null && q.peek() != null)
                available.signal();
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 检索并删除此队列上具有过期延迟的头部元素，
     * 必要时等待，直到此队列上具有过期延迟的头部元素为止。
     *
     * 此方法与{@link #poll(long, TimeUnit)}方法的不同之处在于，该方法会一直等待。
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     */
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (;;) {
                E first = q.peek();
                if (first == null)
                    available.await();
                else {
                    long delay = first.getDelay(NANOSECONDS);
                    if (delay <= 0)
                        return q.poll();
                    first = null; // don't retain ref while waiting
                    if (leader != null)
                        available.await();
                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            available.awaitNanos(delay);
                        } finally {
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null)
                available.signal();
            lock.unlock();
        }
    }

    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }
}
