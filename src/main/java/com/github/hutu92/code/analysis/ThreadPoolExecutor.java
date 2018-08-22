package com.github.hutu92.code.analysis;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by liuchunlong on 2018/8/19.
 */
public class ThreadPoolExecutor {

    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3; // 29
    // 1 左移 29位，然后再减1
    // 1 << 29 ==> 00100000 00000000 00000000 00000000
    // (1 << 29) - 1 ==> 00011111 11111111 11111111 11111111
    // 00011111 11111111 11111111 11111111
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    // -1：
    //      原码：10000000 00000000 00000000 00000001
    //      反码：11111111 11111111 11111111 11111110
    //      补码：11111111 11111111 11111111 11111111
    // -1 << 29 ==> 11100000 00000000 00000000 00000000

    // runState存储在高位中
    // 11100000 00000000 00000000 00000000
    private static final int RUNNING = -1 << COUNT_BITS;
    // 00000000 00000000 00000000 00000000
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    // 00100000 00000000 00000000 00000000
    private static final int STOP = 1 << COUNT_BITS;
    // 01000000 00000000 00000000 00000000
    private static final int TIDYING = 2 << COUNT_BITS;
    // 01100000 00000000 00000000 00000000
    private static final int TERMINATED = 3 << COUNT_BITS;

    // 包装和拆包ctl
    // 保留 c 的高3位
    // 获取线程池的状态
    private static int runStateOf(int c) {
        return c & ~CAPACITY;
    }

    // 保留 c 的低29位
    // 获取线程池中的工作者线程数
    private static int workerCountOf(int c) {
        return c & CAPACITY;
    }

    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempt to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Attempt to CAS-increment the workerCount field of ctl.
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        do {
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;

    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     * <p>
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
            new RuntimePermission("modifyThread");

    private static final boolean ONLY_ONE = true;

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * 使用给定的初始化参数创建一个新的ThreadPoolExecutor。
     *
     * @param corePoolSize    线程池中基本（核心）的线程数，即使它们处于空闲状态，除非设置了allowCoreThreadTimeOut
     * @param maximumPoolSize 线程池中允许的最大线程数
     * @param keepAliveTime   当线程数大于基本线程数时，这是多余空闲线程在终止之前等待新任务的最长时间。
     * @param unit            keepAliveTime参数的时间单位
     * @param workQueue       在执行任务之前用于保存任务的队列。此队列将仅保存execute方法提交的Runnable任务。
     * @param threadFactory   执行程序创建新线程时使用的工厂
     * @param handler         由于达到最大线程数和队列容量而无法继续处理任务时使用的处理程序
     * @throws IllegalArgumentException 如果满足下列条件之一：
     *                                  corePoolSize < 0
     *                                  keepAliveTime < 0
     *                                  maximumPoolSize <= 0
     *                                  maximumPoolSize < corePoolSize
     * @throws NullPointerException     如果workQueue、threadFactory或handler为null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        // 参数校验
        // maximumPoolSize 不能为0，为0没意义
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime); // 纳秒
        this.threadFactory = threadFactory;
        this.handler = handler;
    }


    /**
     * 将来某个时候执行给定的任务。
     * <p>
     * 任务可能在新创建的线程或线程池现有线程中执行。
     * <p>
     * 如果无法提交执行任务，因为线程池已停止或者线程池处于饱和状态，则该任务由当前的RejectedExecutionHandler处理。
     *
     * @param command 要执行的任务
     * @throws RejectedExecutionException 如果无法接受任务执行，由RejectedExecutionHandler自行决定如何处理
     * @throws NullPointerException       如果command为null
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();

        int c = ctl.get();

        /**
         * 1. 如果线程池的线程数小于corePoolSize，则尝试使用给定command作为其第一个任务启动一个新线程。
         *
         * 对addWorker的调用以原子方式检查runState和workerCount，因此通过返回false来防止在不应该添加线程时发生的错误警报。
         */
        if (workerCountOf(c) < corePoolSize) { // 判断线程池的线程数小于corePoolSize
            if (addWorker(command, true)) // 尝试使用给定command作为其第一个任务启动一个新线程
                return;
            c = ctl.get();
        }

        /**
         * 2. 如果任务可以成功加入队列，那么我们仍然需要再次检查是否应该添加一个线程（因为自上次检查后已有的线程已经停止），或者线程池是否在进入该方法后停止。
         *
         * 因此，我们重新检查线程池的状态，如果有必要，如果线程池停止，回滚队列，并拒绝该任务；如果线程池中没有工作线程，且任务队列不为null时，则启动一个新线程。
         */
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            // 重新校验线程池的状态
            // 如果线程池停止（SHUTDOWN、STOP、TIDYING、TERMINATED），回滚队列，并拒绝该任务；
            if (!isRunning(recheck) && remove(command))
                reject(command);
                // 如果线程池中没有工作线程，且任务队列中有任务，则启动一个新线程来处理队列中的任务，此时firstTask为null（即不会创建新的任务）
                // 这里存在两种情况：
                //      1. isRunning()判断线程池运行状态为RUNNING，并向任务队列中添加了一个新的任务；
                //          在当前线程池中没有工作线程的情况下，会创建一个新的工作线程。
                //
                //      2. isRunning()判断线程池运行状态不为RUNNING，remove()执行失败（即任务队列回滚失败），则任务队列中有一个新的任务待处理；
                //          在当前线程池中没有工作线程 & 线程池的状态为SHUTDOWN的情况下，会创建一个新的工作线程。
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        /**
         * 3. 如果我们不能将任务加入队列，那么我们尝试添加一个新线程。如果失败，我们知道线程池已停止或处于饱和，因此拒绝该任务。
         */
        else if (!addWorker(command, false))
            reject(command);
    }

    /**
     * 根据当前线程池状态和给定的线程数限制（corePoolSize或maximumPoolSize），检查是否可以添加新的工作线程。
     * <p>
     * 如果可以，则相应地调整工作线程计数，并且如果可能，创建并启动新工作线程，将firstTask作为其第一个任务运行。
     * <p>
     * 如果线程池已停止或符合关闭条件，则此方法返回false。
     * 如果线程工厂在请求时无法创建线程，它也会返回false。
     * <p>
     * 如果线程创建失败，或者由于线程工厂返回null，或者由于异常（通常是Thread#start中的OutOfMemoryError），我们会回滚。
     *
     * @param firstTask 新工作线程应首先运行的任务（如果没有则为null）。
     *                  使用初始的firstTask（在方法execute()中）创建工作线程，
     *                  以便在线程数少于corePoolSize时（在这种情况下我们始终启动一个工作线程）或队列已满（在这种情况下我们必须绕过队列）时绕过队列。
     *                  最初，空闲线程通常是通过prestartCoreThread创建的，或者用来替换其他dying workers。
     * @param core      如果为true，则使用corePoolSize作为工作线程数的限制条件，否则使用maximumPoolSize。
     *                  (A boolean indicator is used here rather than a value to ensure reads of fresh values after checking other pool state)
     * @return 如果成功则为真
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c); // 获取线程池运行状态

            // Check if queue empty only if necessary.
            // 如果线程池停止（SHUTDOWN、STOP、TIDYING、TERMINATED），则直接返回false。
            // =============== 注意，这里有个特例，当线程池状态为SHUTDOWN时，且firstTask为null & 队列中还有任务时（这里还有个前提：当前线程池中没有工作线程（见execute）），会添加新的工作线程 ===============
            // =============== 而对于线程池状态为RUNNING时，在此方法中一定会添加新的工作线程 ===============
            if (rs >= SHUTDOWN &&
                    !(rs == SHUTDOWN &&
                            firstTask == null &&
                            !workQueue.isEmpty()))
                return false;

            for (; ; ) {
                int wc = workerCountOf(c); // 获取线程池的工作线程数
                if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize)) // 工作线程数校验
                    return false;
                // 尝试CAS将工作线程数+1
                // 如果成功，则结束retry循环
                if (compareAndIncrementWorkerCount(c))
                    break retry;

                // 注意：因为 c 的高3位表示线程池状态，低29位表示线程池工作线程数
                // 所以如果CAS失败，有两个原因：1. 线程池状态改变；2. 线程池工作线程数改变

                // 1. 线程池状态改变；
                // 重新获取线程池状态，如果状态改变则继续retry循环
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // 2. 线程池工作线程数改变
                // CAS是由于workerCount更改而失败; 重试内循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            final ReentrantLock mainLock = this.mainLock; // 全局锁
            w = new Worker(firstTask); // 创建工作线程
            final Thread t = w.thread;
            // 如果线程工厂在请求时无法创建线程，返回false
            if (t != null) {
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int c = ctl.get();
                    int rs = runStateOf(c); // 再次获取线程池运行状态

                    // 线程池运行状态为RUNNING，
                    // 或者为SHUTDOWN，但firstTask为null（这里有就针对上面提到的特例），会添加工作线程
                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * 回滚工作线程的创建。
     * - 如果存在，则将worker工作线程从workers中移除；
     * - 减少工作线程的数量；
     * - 重新检查是否终止，以防该工作线程的存在阻止终止。
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * 如果第一个任务不为null则执行第一个任务，否则从任务队列中获取任务执行。
     * <p>
     * 如果任务执行抛出异常，则当前线程退出
     *
     * @param w
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread(); // 当前工作线程
        Runnable task = w.firstTask; // 线程创建时指定的第一个任务
        w.firstTask = null; // 执行完第一个任务，需要将其清除，后续从任务队列中获取任务执行
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true; // 标记工作线程的退出是否是因为执行用户的任务抛出的异常造成的
        try {
            // 如果第一个任务不为null则执行第一个任务，否则从任务队列中获取任务执行
            while (task != null || (task = getTask()) != null) { // getTask会阻塞的获取队列中的任务，如果任务为null，退出当前工作线程
                w.lock();
                // 如果线程池状态为STOP、TIDYING、TERMINATED，请确保线程被中断（如果当前线程未中断，则中断当前线程）；
                // 否则线程池的状态不为STOP、TIDYING、TERMINATED，确保线程没有被中断（如果线程被中断了，则清除中断标记）。
                // 在第二种情况下这需要重新检查以在清除中断时处理shutdownNow的竞争。
                //
                // 这里的意图是为了：
                //      对于线程池状态为STOP、TIDYING、TERMINATED【针对 shutdownNow】，希望正在执行任务的线程能够响应中断，立刻退出线程（当然前提条件是该任务能够响应中断，当然无法响应中断的任务可能永远不会终止）；
                //      对于线程池的状态不为STOP、TIDYING、TERMINATED【针对 shutdown】，则要保证线程能够顺利的执行正在执行的任务。
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) { // 任务处理抛出异常，线程退出
                        thrown = x;
                        throw x;
                    } catch (Error x) {
                        thrown = x;
                        throw x;
                    } catch (Throwable x) {
                        thrown = x;
                        throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null; // task置空，以后都从队列中获取任务
                    w.completedTasks++; // 当前线程处理的任务数+1
                    w.unlock();
                }
            }
            /**
             * 标记工作线程的退出是否是因为执行用户的任务抛出的异常造成的
             *
             * 如果是因为执行用户的任务抛出的异常造成的，则为true；
             *
             * 否则，因为如下原因线程正常退出，则为false；
             *      1. 工作线程数超过maximumPoolSize（由于调用setMaximumPoolSize）。
             *      2. 线程池已停止（stop）。
             *      3. 线程池已关闭（shutdown），并且队列为空。
             *      4. 该工作线程等待任务超时，超时工作线程在超时等待之前和之后都会被终止（即，allowCoreThreadTimeOut || workerCount > corePoolSize）。
             */
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    /**
     * 根据当前配置设置，执行阻塞或定时等待任务，如果此工作线程因以下任何一个原因而必须退出，则返回null：
     * <p>
     * 1. 工作线程数超过maximumPoolSize（由于调用setMaximumPoolSize）。
     * 2. 线程池已停止（stop）。
     * 3. 线程池已关闭（shutdown），并且队列为空。
     * 4. 该工作线程等待任务超时，超时工作线程在超时等待之前或之后会被终止。
     * 注意，超时的线程是满足 allowCoreThreadTimeOut || workerCount > corePoolSize 条件的线程，
     * 即：如果允许核心（基本）线程超时，则核心线程会超时退出，或者如果线程数大于核心线程数（corePoolSize），则线程也会超时退出，直至满足小于等于核心线程数
     *
     * @return 任务, 如果worker必须退出则返回null，在这种情况下，workerCount递减
     */
    private Runnable getTask() {
        // 是否已超时
        boolean timedOut = false; // Did the last poll() time out?

        retry:
        for (; ; ) {
            int c = ctl.get();
            int rs = runStateOf(c); // 线程池运行状态

            // Check if queue empty only if necessary.
            // 如果满足以下两种条件，则将工作线程数-1，退出当前工作线程（方法getTask()返回null，此时没有任务去执行，会退出当前线程）：
            //      1. rs >= STOP；
            //      2. rs == SHUTDOWN && workQueue.isEmpty()
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            // 当满足 allowCoreThreadTimeOut || wc > corePoolSize 时，线程会在超时时退出
            boolean timed;      // Are workers subject to culling?

            for (; ; ) {
                int wc = workerCountOf(c); // 当前线程池的工作线程数
                timed = allowCoreThreadTimeOut || wc > corePoolSize; // 线程是否会在超时时退出

                /**
                 * 如果线程池当前工作线程数不超过最大线程数 & 当前线程未超时，则退出当前循环，否则工作线程数-1，退出当前工作线程
                 */

                // 如果线程池当前工作线程数不超过最大线程数 & 当前线程未超时，则退出当前循环
                if (wc <= maximumPoolSize && !(timedOut && timed))
                    break;
                // 否则工作线程数-1，退出当前工作线程（getTask返回null，此时没有任务去执行，即表示退出当前线程）
                if (compareAndDecrementWorkerCount(c))
                    return null;
                // CAS将工作线程数-1失败，说明线程池ctl发生了变化，如果是线程池的状态改变，则重试retry，否则是线程池的workerCount改变，则继续内部循环重试
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }

            try {
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                // 如果工作线程从队列中获取任务时被中断，并不会退出，而是标记自己未超时，继续重新从队列中获取任务。
                timedOut = false;
            }
        }
    }

    /**
     * 为即将退出的工作线程(dying worker)执行退出清理工作。
     * <p>
     * 仅从工作线程中调用。
     * <p>
     * 除非设置了completedAbruptly，否则假定workerCount已经调整为退出状态（即减1）。
     * <p>
     * 此方法从workers中删除工作线程，并且如果由于用户任务异常线程退出或者正在运行的工作线程数少于corePoolSize或者队列非空但线程池中没有工作线程，则可能终止线程池或新增工作线程。
     *
     * @param w                 the worker
     * @param completedAbruptly 标记工作线程的退出是否是因为执行用户的任务抛出的异常造成的
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果工作线程的退出是因为执行用户的任务抛出的异常造成的，则workerCount还没来得及-1，这里执行workerCount-1
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) { // 如果线程池状态为RUNNING或者SHUTDOWN
            if (!completedAbruptly) { // 如果线程是正常退出的
                // 如果线程池允许核心线程超时，则线程池允许的最小线程数为0，否则为corePoolSize
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                // 此外，如果任务队列中还有任务，则min的最小值为1，而不是0
                if (min == 0 && !workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 如果工作线程的退出是因为执行用户的任务抛出的异常造成的，或者不满足线程池的最小线程数，则添加一个新的工作线程
            addWorker(null, false);
        }
    }

    protected void beforeExecute(Thread t, Runnable r) {
    }

    protected void afterExecute(Runnable r, Throwable t) {
    }

    /**
     * 如果（线程池状态为SHUTDOWN、工作线程为空、任务队列为空），或者（线程池状态为STOP、工作线程为空），则转换为TERMINATED状态。
     * 否则如果工作线程存在(workerCount非零)，则中断一个空闲的工作线程以确保shutdown信号传播。
     * <p>
     * 必须在使终止成为可能的任何操作（在执行关闭线程池期间减少工作线程数量或从任务队列中删除任务）之后调用此方法，如果满足线程池终止的条件就会终止线程池。
     * <p>
     * 该方法是非私有的，以允许ScheduledThreadPoolExecutor访问。
     */
    final void tryTerminate() {
        for (; ; ) {
            int c = ctl.get();
            /*
                这里的逻辑有点绕：
                    如果线程处于RUNNING状态，此时肯定是不需要执行tryTerminate的，直接返回；
                    如果线程处于TIDYING/TERMINATED状态，说明有其他线程执行tryTerminate终止线程池，直接返回；

                    此时，还剩下 SHUTDOWN 和 STOP 状态。

                    如果线程处于SHUTDOWN状态，但是任务队列不为空，也直接返回；

                    此时，线程池处于如下两种状态：
                        1. 线程池处于SHUTDOWN状态，任务队列为空；
                        2. 线程池处于STOP状态；

                    又如果（线程池状态为SHUTDOWN、工作线程为空、任务队列为空），或者（线程池状态为STOP、工作线程为空），则转换为TERMINATED状态。

                    ==================== 所以此时决定是否转换为TERMINATED状态的唯一决定性因素是**工作线程是否为空**!!! ====================
             */
            if (isRunning(c) || // 线程处于RUNNING状态
                    runStateAtLeast(c, TIDYING) || // 线程处于TIDYING/TERMINATED状态
                    (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty())) // 线程处于SHUTDOWN状态，但是任务队列不为空
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // TIDYING = 01000000 00000000 00000000 00000000
                // TIDYING | 0 ==> TIDYING
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        // 01100000 00000000 00000000 00000000
                        // TERMINATED | 0 ==> TERMINATED
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll(); // 用于通知awaitTermination方法的等待
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // CAS更新线程池状态失败，重试
            // else retry on failed CAS
        }
    }

    /**
     * 中断可能正在等待任务的线程（as indicated by not being locked），
     * 以便它们可以检查线程池终止（线程池处于SHUTDOWN状态，任务队列为空；线程池处于STOP状态；）或配置更改（ie, 调用setMaximumPoolSize()方法）。
     * 从而退出线程。
     * <p>
     * 忽略SecurityExceptions（在这种情况下，某些线程可能不会被中断）。
     *
     * @param onlyOne 如果为true，则最多中断一个工作线程。
     *                只有在其他方面（线程池处于SHUTDOWN状态，任务队列为空；线程池处于STOP状态；）允许终止但仍有其他工作线程时，tryTerminate才会调用此方法。
     *                在这种情况下，在当前所有线程都在等待的情况下，最多会中断一个等待工作线程来传播shutdown信号。
     *                中断任意线程可以确保shutduwn开始后新到达的工作线程最终也会退出。
     *                为了保证最终终止，只需要中断一个空闲工作线程就足够了，但shutdown()会中断所有空闲工作线程，以便冗余工作线程立即退出，而不是等待拖延任务完成。
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) { // 由于 w.tryLock() 加锁，这不会中断正在执行任务的线程（因为正在执行任务的线程也会通过 w.tryLock()）
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    protected void terminated() {
    }

    /**
     * 实现了Runnable接口，并继承了AQS
     */
    private final class Worker
            extends AbstractQueuedSynchronizer
            implements Runnable {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /**
         * Thread this worker is running in.  Null if factory fails.
         */
        final Thread thread;
        /**
         * Initial task to run.  Possibly null.
         */
        Runnable firstTask;
        /**
         * Per-thread task counter
         */
        volatile long completedTasks;

        /**
         * 使用给定的firstTask和ThreadFactory创建的线程创建工作线程。
         *
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            // 默认使用 DefaultThreadFactory 线程工厂
            // 使用 Worker 作为 Runnable 构造线程
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * Delegates main run loop to outer runWorker
         */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() {
            acquire(1);
        }

        public boolean tryLock() {
            return tryAcquire(1);
        }

        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return isHeldExclusively();
        }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /////////////////////////////////////////////////////////
    // 关闭线程池 shutdown
    /////////////////////////////////////////////////////////

    /**
     * 执行线程池的关闭，其中先前提交的任务将被执行，但不会接受任何新任务。
     * <p>
     * 如果已经关闭，再次调用没有其他影响。
     * <p>
     * 此方法不会等待先前提交的任务执行完成再返回。可以使用awaitTermination来做到这一点。
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            // 由于 w.tryLock() 加锁，这不会中断正在执行任务的线程（因为正在执行任务的线程也会通过 w.tryLock()）
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * 如果有安全管理器，一般而言，请确保调用者有权关闭线程（请参阅shutdownPerm）。
     * 如果通过，另外确保允许调用者中断每个工作线程。
     * 如果SecurityManager特别处理某些线程，即使第一次检查通过也可能不是true。
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager(); // 获取SecurityManager
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * 将runState转换为给定目标值，或者如果已经至少是给定的目标值则将其保留。
     *
     * @param targetState 所需的状态，SHUTDOWN或STOP（但不是TIDYING或TERMINATED - 这些是使用tryTerminate设置的）
     */
    private void advanceRunState(int targetState) {
        for (; ; ) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    // ctlOf(targetState, workerCountOf(c)) ==> targetState | workerCountOf(c)
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /////////////////////////////////////////////////////////
    // 关闭线程池 shutdownNow
    /////////////////////////////////////////////////////////

    /**
     * 尝试停止所有正在执行的任务(actively executing tasks)，停止等待任务的处理，并返回等待执行的任务列表。
     * <p>
     * 从此方法返回时，这些任务将从任务队列中删除。
     * <p>
     * 此方法不等待正在执行的任务终止。使用awaitTermination来做到这一点。
     * <p>
     * 除了尽最大努力尝试停止正在执行的任务之外，没有任何保证。此实现通过Thread.interrupt取消任务，因此任何无法响应中断的任务都可能永远不会终止。
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    /**
     * 即使线程处于active（正在执行任务），也会中断所有线程。忽略SecurityExceptions（在这种情况下，某些线程可能不会被中断）。
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted(); // 此时的中断不需要获取工作线程的锁，所以当工作线程正在执行任务时，也可以对其中断（前期条件是当前任务响应中断，当然无法响应中断的任务可能永远不会终止）
        } finally {
            mainLock.unlock();
        }
    }
}
