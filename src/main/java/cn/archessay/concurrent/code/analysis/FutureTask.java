package cn.archessay.concurrent.code.analysis;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by 刘春龙 on 2018/8/23.
 */
public class FutureTask {

    /** The underlying callable; nulled out after running */
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    private volatile WaitNode waiters;

    /**
     * 此任务的运行状态，初始为NEW。
     * <p>
     * 运行状态仅在方法set、setException和cancel中转换为终止状态。
     * <p>
     * 在完成期间，状态可能处于COMPLETING（当设置{@code outcome}时）或INTERRUPTING（调用{@link #cancel(true)}中断工作线程时）瞬时态。
     * 从这些中间状态到最终状态的转换使用更廉价的有序/惰性写入，因为这些中间态值是唯一的并且不能进一步修改。
     * <p>
     * 可能的状态转换：
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state;
    private static final int NEW = 0;
    private static final int COMPLETING = 1;
    // case 1 return value
    private static final int NORMAL = 2;
    // case 2 throw ExecutionException
    private static final int EXCEPTIONAL = 3;
    // case 3 throw CancellationException
    private static final int CANCELLED = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED = 6;

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /////////////////////////////////////////////////////////
    // 执行任务
    /////////////////////////////////////////////////////////
    public void run() {
        // 如果任务不是初始状态NEW，或者将runner设置为当前线程失败，放弃执行
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            // c 不为空，且状态为NEW
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call(); // 执行 c
                    ran = true;
                } catch (Throwable ex) { // 任务执行抛出异常
                    // NEW -> COMPLETING -> EXCEPTIONAL
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    // NEW -> COMPLETING -> NORMAL
                    set(result);
            }
        } finally {
            // runner必须为非null，直到状态完成转换，以防止并发调用run()
            runner = null;
            // 再将runner置null后，必须重新读取状态以防止忽略中断
            // 因为上面的set、setException方法可能因为cancel(true)中断任务而CAS更新任务状态失败。
            int s = state;
            if (s >= INTERRUPTING) // 任务被cancel(true)中断了，则等待中断操作完成
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) { // 将任务状态由NEW改为COMPLETING
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) { // 将任务状态由NEW改为COMPLETING
            outcome = t; // 输出异常 t
            /*
                public native void    putOrderedObject(Object o, long offset, Object x);
                public native void    putOrderedInt(Object o, long offset, int x);
                public native void    putOrderedLong(Object o, long offset, long x);

                putOrderedObject(Object obj, long offset, Object x)：
                    设置obj对象中offset偏移地址对应的object型field的值为指定值。
                    这是一个有序或者有延迟的putObjectVolatile方法，并且不保证值的改变被其他线程立即看到。
                    只有在field被volatile修饰并且期望被意外修改的时候使用才有用。
                    这个方法在对低延迟代码是很有用的，它能够实现非堵塞的写入，这些写入不会被Java的JIT重新排序指令(instruction reordering)，
                    这样它使用快速的存储-存储(store-store) barrier, 而不是较慢的存储-加载(store-load) barrier, 后者总是用在volatile的写操作上，
                    这种性能提升是有代价的，虽然廉价，也就是写后结果并不会被其他线程看到，甚至是自己的线程，通常是几纳秒后被其他线程看到，这个时间比较短，所以代价可以忍受。
                    类似Unsafe.putOrderedObject还有unsafe.putOrderedLong等方法，unsafe.putOrderedLong比使用 volatile long要快3倍左右。
             */
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }

    /**
     * 删除并唤醒所有的等待线程，调用done()方法，将callable置null
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) { // 将waiters置null
                // 唤醒waiters等待队列中的每一个等待的线程
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /////////////////////////////////////////////////////////
    // 获取执行结果
    /////////////////////////////////////////////////////////
    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING) // 如果任务的状态为NEW、COMPLETING，则任务还未完成，无法通过report判断任务执行的结果,需要等待
            s = awaitDone(false, 0L);
        return report(s);
    }

    /**
     * 针对已完成的任务，返回执行结果或者抛出异常
     *
     * @param s 任务完成得状态
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)
            return (V)x;
        if (s >= CANCELLED)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }

    /**
     * 等待任务完成，或者当任务被中断、等待超时时取消
     *
     * @param timed 如果使用超时等待，则为true
     * @param nanos 如果使用超时等待的，指定等待的时间
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        // 如果使用超时等待的话，计算超时等待的截止时间
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null; // waiters等待队列中当前线程对应的等待节点
        boolean queued = false; // 当前线程是否已加入到waiters等待队列中
        for (;;) {
            // 如果当前线程被中断，删除等待节点，抛出中断异常
            if (Thread.interrupted()) {
                /**
                 * 如果p为null，表示当前线程还未加入到waiters等待队列中，removeWaiter什么也不会做；
                 * 否则，删除等待节点；
                 */
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state; // 当前任务的执行状态
            // 就如刚才提到，如果任务的状态为NEW、COMPLETING，则任务还未完成，无法通过report判断任务执行的结果,需要等待。
            // 如果任务的状态大于COMPLETING，则可以根据任务的状态返回相应的执行结果。
            // 这里只是简单的清空当前等待节点的thread，并不会删除队列中的当前的等待节点
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            // 就如刚才提到，如果任务的状态为NEW、COMPLETING，则任务还未完成，无法通过report判断任务执行的结果,需要等待。
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                // 将当前线程的等待节点加入到waiters等待队列中
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) { // 等待超时，从等待队列中删除当前等待节点，并返回任务状态
                    removeWaiter(q);
                    return state;
                }
                // 否则，超时等待
                LockSupport.parkNanos(this, nanos);
            }
            else
                // 不是超时等待，则永久等待，直到任务执行完成/异常退出/任务取消，唤醒所有等待的线程
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null; // 先清空当前等待节点的thread
            retry:
            for (;;) {          // restart on removeWaiter race
                /**
                 * 上面我们提到，awaitDone方法在任务的状态大于COMPLETING时，则根据任务的状态返回相应的执行结果，
                 * 同时只是简单的清空当前等待节点的thread，并不会删除队列中的当前的等待节点。
                 *
                 * 这里循环遍历，删除这些节点
                 */
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next; // 将s指向等待队列当前遍历的节点的next节点（后继节点）
                    // 如果q.thread不为null，将q指向等待队列当前遍历的节点
                    if (q.thread != null)
                        pred = q;

                    /**
                     * 否则，q.thread为null，说明当前遍历节点的thread为null，应该删除当前遍历的节点
                     *
                     * 则此时pred代表的是当前遍历节点的前驱节点
                     */

                    // 如果pred不为null，
                    // 将当前遍历节点的前驱节点的next指向当前遍历节点的后继节点，即删除当前遍历的节点
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    // 如果pred为null，则直接删除，即将s设置为等待队列的头节点
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    /////////////////////////////////////////////////////////
    // 取消任务
    /////////////////////////////////////////////////////////

    /**
     * 任务只有处于NEW状态才允许取消
     *
     * @param mayInterruptIfRunning
     * @return
     */
    public boolean cancel(boolean mayInterruptIfRunning) {

        // 如果任务状态不等于NEW，说明任务状态大于等于COMPLETING，
        // 进一步说明，任务正在执行或者已执行完set或setException方法（转换为终止状态），
        // 再或者，任务正在执行或者已执行完cancel方法，
        // 不再允许取消任务
        if (state != NEW)
            return false;
        if (mayInterruptIfRunning) { // 允许中断正在执行的任务
            if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, INTERRUPTING))
                return false;
            Thread t = runner;
            if (t != null)
                t.interrupt(); // 中断任务线程
            UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED); // final state
        } else if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, CANCELLED)) // 设置任务状态为CANCELLED
            return false;
        finishCompletion(); // 删除并唤醒所有的等待线程，调用done()方法，将callable置null
        return true;
    }
}