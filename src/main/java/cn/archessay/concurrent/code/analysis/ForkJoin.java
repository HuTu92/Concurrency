package cn.archessay.concurrent.code.analysis;

import java.util.concurrent.*;

/**
 * Created by 刘春龙 on 2018/8/17.
 */
public class ForkJoin {

    /////////////////////////////////////////////////////////
    // ForkJoinPool 构造函数
    /////////////////////////////////////////////////////////

    /**
     * 使用默认的线程工厂、不使用UnconghtExceptionHandler，并且采用非异步LIFO处理模式，
     * 创建一个并行性等于Runtime.availableProcessors的ForkJoinPool。
     *
     * @throws SecurityException 如果存在安全管理器，并且不允许调用者修改线程，因为它不包含RuntimePermission("modifyThread")，则抛出异常
     */
    public ForkJoinPool() {
        /*
            Runtime.getRuntime().availableProcessors()
                返回Java虚拟机可用的处理器数。在特定的虚拟机调用期间，此值可能会更改。因此，对可用处理器数量敏感的应用程序应偶尔轮询此属性并适当调整其资源使用情况。
                返回虚拟机可用的最大处理器数量; 永远不会小于一个。
         */
        this(Math.min(MAX_CAP, Runtime.getRuntime().availableProcessors()),
                defaultForkJoinWorkerThreadFactory, null, false);
    }

    /**
     * 使用给定参数创建ForkJoinPool。
     *
     * @param parallelism 并行性水平。默认值使用 Runtime.availableProcessors。
     * @param factory     用于创建新线程的工厂。默认值使用 defaultForkJoinWorkerThreadFactory。
     * @param handler     用于执行任务时遇到不可恢复的错误而终止的内部工作线程的处理程序。默认值使用null。
     * @param asyncMode   如果为true，则为还未执行join的forked任务（已执行fork）建立本地先进先出（FIFO）调度模式。
     *                    在工作线程仅处理事件类型的异步任务应用程序中，此模式可能比默认的基于本地堆栈的模式更合适。
     *                    默认值使用false。
     * @throws IllegalArgumentException 如果并行度小于或等于零，或大于最大限制抛出该异常
     * @throws NullPointerException     如果线程工厂为null抛出该异常
     * @throws SecurityException        如果存在安全管理器，并且不允许调用者修改线程，因为它不包含 RuntimePermission("modifyThread")，则抛出异常
     */
    public ForkJoinPool(int parallelism,
                        ForkJoinPool.ForkJoinWorkerThreadFactory factory,
                        Thread.UncaughtExceptionHandler handler,
                        boolean asyncMode) {
        this(checkParallelism(parallelism),
                checkFactory(factory),
                handler,
                asyncMode ? FIFO_QUEUE : LIFO_QUEUE,
                "ForkJoinPool-" + nextPoolId() + "-worker-");
        checkPermission();
    }

    /**
     * 使用给定参数创建ForkJoinPool，无需任何安全检查或参数验证。 由makeCommonPool直接调用。
     */
    private ForkJoinPool(int parallelism,
                         ForkJoinWorkerThreadFactory factory,
                         UncaughtExceptionHandler handler,
                         int mode,
                         String workerNamePrefix) {
        this.workerNamePrefix = workerNamePrefix;
        this.factory = factory;
        this.ueh = handler;
        // parallelism & SMASK：parallelism的低16位
        // LIFO_QUEUE = 0; FIFO_QUEUE = 1 << 16;
        // (parallelism & SMASK) | mode：parallelism的低16位(LIFO) 或者 parallelism的低16位 + 0x10000 (FIFO)
        this.config = (parallelism & SMASK) | mode;
        long np = (long) (-parallelism); // offset ctl counts
        this.ctl = ((np << AC_SHIFT) & AC_MASK) | ((np << TC_SHIFT) & TC_MASK);
    }

    // 如果并行度小于或等于零，或大于最大限制抛出IllegalArgumentException异常
    private static int checkParallelism(int parallelism) {
        if (parallelism <= 0 || parallelism > MAX_CAP)
            throw new IllegalArgumentException();
        return parallelism;
    }

    // 如果线程工厂为null抛出NullPointerException异常
    private static ForkJoinWorkerThreadFactory checkFactory
    (ForkJoinWorkerThreadFactory factory) {
        if (factory == null)
            throw new NullPointerException();
        return factory;
    }

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
    }

    // Nested classes

    /**
     * Factory for creating new {@link ForkJoinWorkerThread}s.
     * A {@code ForkJoinWorkerThreadFactory} must be defined and used
     * for {@code ForkJoinWorkerThread} subclasses that extend base
     * functionality or initialize threads with different contexts.
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         *
         * @param pool the pool this thread works in
         * @return the new worker thread
         * @throws NullPointerException if the pool is null
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * Default ForkJoinWorkerThreadFactory implementation; creates a
     * new ForkJoinWorkerThread.
     */
    static final class DefaultForkJoinWorkerThreadFactory
            implements ForkJoinWorkerThreadFactory {
        public final ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ForkJoinWorkerThread(pool);
        }
    }

    // Constants shared across ForkJoinPool and WorkQueue

    // Bounds
    static final int SMASK = 0xffff;        // short bits == max index，11111111 11111111
    static final int MAX_CAP = 0x7fff;        // max #workers - 1，32767

    // Mode bits for ForkJoinPool.config and WorkQueue.config
    static final int LIFO_QUEUE = 0;
    static final int FIFO_QUEUE = 1 << 16;

    /**
     * Creates a new ForkJoinWorkerThread. This factory is used unless
     * overridden in ForkJoinPool constructors.
     */
    public static final ForkJoinWorkerThreadFactory
            defaultForkJoinWorkerThreadFactory;

    /**
     * Permission required for callers of methods that may start or
     * kill threads.
     */
    private static final RuntimePermission modifyThreadPermission;

    /**
     * Sequence number for creating workerNamePrefix.
     */
    private static int poolNumberSequence;

    /**
     * Returns the next sequence number. We don't expect this to
     * ever contend, so use simple builtin sync.
     */
    private static final synchronized int nextPoolId() {
        return ++poolNumberSequence;
    }

    static {
        // ...
        defaultForkJoinWorkerThreadFactory =
                new DefaultForkJoinWorkerThreadFactory();
        modifyThreadPermission = new RuntimePermission("modifyThread");
        // ...
    }
}
