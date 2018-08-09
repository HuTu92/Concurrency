package com.github.hutu92;

import java.util.concurrent.locks.LockSupport;

/**
 * Created by 刘春龙 on 2018/8/8.
 */
public class ConditionObject {
//
//    /**
//     * Mode meaning to reinterrupt on exit from wait
//     */
//    private static final int REINTERRUPT = 1;
//    /**
//     * Mode meaning to throw InterruptedException on exit from wait
//     */
//    private static final int THROW_IE = -1;
//
//    /**
//     * First node of condition queue.
//     */
//    private transient Node firstWaiter;
//    /**
//     * Last node of condition queue.
//     */
//    private transient Node lastWaiter;
//
//    /**
//     * 从头遍历，移除等待队列中取消的节点
//     */
//    private void unlinkCancelledWaiters() {
//        Node t = firstWaiter; // t为当前遍历的节点，初始为等待队列的首节点
//        Node trail = null; // trail为当前已经遍历的等待状态为CONDITION（也即未取消）的节点的最后一个
//        while (t != null) {
//            Node next = t.nextWaiter; // 当前遍历节点的下一个节点
//            // 如果当前遍历的节点的状态为取消，则移除当前节点
//            if (t.waitStatus != Node.CONDITION) {
//                t.nextWaiter = null;
//                if (trail == null)
//                    /*
//                        case 1:
//                            trail为null，说明不存在当前已经遍历的等待状态为CONDITION（也即未取消）的节点，
//                            再加上，当前遍历的节点的状态为取消，需要被移除
//                            故，此时首节点为当前节点的next
//                     */
//                    firstWaiter = next;
//                else
//                    /*
//                        case 2:
//                            trail不为null，则将当前已经遍历的等待状态为CONDITION（也即未取消）的节点的next指向当前节点的next，即移除当前节点
//                     */
//                    trail.nextWaiter = next;
//                if (next == null)
//                    /*
//                        case 3:
//                            如果next为null，说明当前遍历的节点没有下一节点，
//                            再加上，当前遍历的节点的状态为取消，需要被移除
//                            所以尾节点只能指向trail
//                     */
//                    lastWaiter = trail;
//            } else // 如果当前遍历的节点的状态为CONDITION，不做任何处理，trail设置为当前节点，继续向后遍历其它节点
//                trail = t;
//            t = next;
//        }
//    }
//
//    /**
//     * 获取当前的同步状态，使用该同步状态值调用release；并返回该同步状态值。
//     * <p>
//     * 如果失败，则取消等待队列中的该节点，并抛出异常。
//     *
//     * @param node the condition node for this wait
//     * @return previous sync state
//     */
//    final int fullyRelease(Node node) {
//        boolean failed = true; // 默认释放锁失败
//        try {
//            int savedState = getState(); // 获取当前的同步状态
//            if (release(savedState)) { // 释放同步状态，也即释放锁
//                failed = false; // 标记释放锁成功
//                return savedState;
//            } else { // 如果释放锁失败，则取消等待队列中的该节点，并抛出IllegalMonitorStateException异常。
//                throw new IllegalMonitorStateException();
//            }
//        } finally {
//            if (failed)
//                node.waitStatus = Node.CANCELLED;
//        }
//    }
//
//    /**
//     * 向等待队列中加入新的节点
//     *
//     * @return its new wait node
//     */
//    private Node addConditionWaiter() {
//        Node t = lastWaiter; // 等待队列的尾节点
//        // 如果尾节点的等待状态不是CONDITION（也即取消），
//        // 则从头遍历等待队列，删除取消的节点
//        if (t != null && t.waitStatus != Node.CONDITION) {
//            unlinkCancelledWaiters();
//            t = lastWaiter; // 重新指向新的尾节点
//        }
//        Node node = new Node(Thread.currentThread(), Node.CONDITION);
//        if (t == null) // 尾节点为空，即等待队列为空，则初始化等待队列。
//            firstWaiter = node;
//        else
//            t.nextWaiter = node;
//        lastWaiter = node;
//        return node;
//    }
//
//    /**
//     * 如果节点通过从尾部向后搜索能查到在同步队列中，则返回true。仅由isOnSyncQueue调用。
//     *
//     * @return true if present
//     */
//    private boolean findNodeFromTail(Node node) {
//        Node t = tail;
//        for (; ; ) {
//            if (t == node)
//                return true;
//            if (t == null)
//                return false;
//            t = t.prev;
//        }
//    }
//
//    /**
//     * 如果节点（该节点是一个最初处于等待队列中的节点）在同步队列中正在等待重新获取同步状态（获取锁），则返回true。
//     * <p>
//     * <p>
//     * 1. node.next 不为 null，即该节点存在后继节点，则该节点肯定在队列中，返回true；
//     * 2. 调用{@link #findNodeFromTail}，从尾部向前搜索同步队列来查找该节点，如果找到该节点，返回true；
//     *
//     * @param node the node
//     * @return true if is reacquiring
//     */
//    final boolean isOnSyncQueue(Node node) {
//        // 为什么会有下面的这两个条件？
//        // 其实我们可以参考ConditionObject.signal()的代码实现（transferForSignal()方法会将Node.CONDITION替换为0，以及enq()方法）。
//        // 这里的判断就是确保node节点已经正确的从等待队列移到同步队列中。
//        if (node.waitStatus == Node.CONDITION || node.prev == null)
//            return false;
//        if (node.next != null) // If has successor, it must be on queue
//            return true;
//        /*
//         * node.prev can be non-null, but not yet on queue because
//         * the CAS to place it on queue can fail. So we have to
//         * traverse from tail to make sure it actually made it.  It
//         * will always be near the tail in calls to this method, and
//         * unless the CAS failed (which is unlikely), it will be
//         * there, so we hardly ever traverse much.
//         */
//        return findNodeFromTail(node);
//    }
//
//    /**
//     * 此方法被调用，说明当前线程被中断了（但是中断标记位已被清除）。
//     *
//     * Tip1：如果是当前线程将节点node移到同步队列，则返回true
//     * <p>
//     * Tip2：如果是其它线程调用signal()方法将当前节点node加入到同步队列，则返回false
//     *
//     * 该方法会保证返回时，节点node已在同步队列中
//     *
//     * @param node the node
//     * @return true if cancelled before the node was signalled
//     */
//    final boolean transferAfterCancelledWait(Node node) {
//        // Tip1：
//        // 同步状态CAS成功，说明signal()方法中的transferForSignal方法还没执行，此后，**也不会再执行**。
//        // 所以将节点node移到同步队列的任务在这里执行
//        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
//            enq(node);
//            return true; // 此时，节点node已在同步队列中
//        }
//        /*
//         * If we lost out to a signal(), then we can't proceed
//         * until it finishes its enq().  Cancelling during an
//         * incomplete transfer is both rare and transient, so just
//         * spin.
//         */
//        // Tip2：
//        // 上述同步状态CAS失败，说明其它线程调用signal()方法，其中的transferForSignal()已经在执行了，
//        // 需要等待transferForSignal()方法将当前节点node加入到同步队列中
//        while (!isOnSyncQueue(node))
//            Thread.yield();
//        return false; // 此时，节点node已在同步队列中
//    }
//
//    /**
//     * 检查线程是否中断，返回中断模式
//     * <p>
//     * 如果线程中断：
//     * 如果是当前线程将节点node移到同步队列，则返回THROW_IE；
//     * 如果是其它线程调用signal()方法将当前节点node加入到同步队列，则返回REINTERRUPT；
//     * 如果没有中断，则返回0；
//     */
//    private int checkInterruptWhileWaiting(Node node) {
//        return Thread.interrupted() ?
//                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
//                0;
//    }
//
//    /**
//     * 要么抛出InterruptedException，要么重新中断当前线程，或者什么都不做，这具体取决于模式。
//     * <p>
//     * THROW_IE：抛出中断异常
//     * REINTERRUPT：重新设置中断标记位，这不会抛出异常
//     */
//    private void reportInterruptAfterWait(int interruptMode)
//            throws InterruptedException {
//        if (interruptMode == THROW_IE)
//            throw new InterruptedException();
//        else if (interruptMode == REINTERRUPT)
//            selfInterrupt();
//    }
//
//    /**
//     * 进入等待状态，并释放锁
//     *
//     * @throws InterruptedException
//     */
//    public final void await() throws InterruptedException {
//        if (Thread.interrupted())
//            throw new InterruptedException();
//        // 向等待队列中加入新的节点
//        // 此外，如果等待队列尾节点的等待状态不是CONDITION（也即取消），则从头遍历等待队列，删除取消的节点
//        Node node = addConditionWaiter();
//        // 获取当前的同步状态值，使用该同步状态值作为参数调用release释放同步状态；并返回该同步状态值。
//        // 如果失败，则取消等待队列中的该节点，并抛出异常。
//        int savedState = fullyRelease(node); // 此时当前线程已释放锁，savedState保存当前释放锁的同步状态
//        int interruptMode = 0;
//
//        // ==================至此，其它线程获取锁后，会在某一时刻（当前节点为等待队列的首节点时）调用signal()方法将等待队列中的当前节点移到同步队列尾部==================
//
//        while (!isOnSyncQueue(node)) {
//            // 如果当前节点还未进入同步队列，则当前线程先park。
//            // 等待其它获取锁的线程调用signal方法将等待队列中的当前节点移到同步队列尾部，然后再唤醒当前线程
//            // 或者等待线程被中断
//            LockSupport.park(this);
//
//            /*
//                当前线程park被打断（唤醒）存在两种情：
//                    1. 线程中断；
//                    2. 其它获取锁的线程调用signal方法唤醒当前线程
//             */
//            // 针对中断的逻辑
//            // 如果interruptMode不为0，则transferAfterCancelledWait方法执行了，其保证自身返回时，node已在同步队列中，故直接break即可。
//            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
//                break;
//        }
//
//        // ==================至此，节点进入了同步队列==================
//
//        /*
//            节点进入同步队列之后，就进入了一个自旋的过程，
//            每个节点（或者说每个线程）都在自省地观察，当条件满足，获取到了同步状态，就可以从这个自旋过程中退出，
//            否则依旧留在这个自旋过程中（并会阻塞节点的线程），
//            同时自旋时，还会设置前驱节点的同步状态为SIGNAL，代表该节点线程处于park等待状态，需要前驱节点唤醒。
//         */
//
//        // 这里使用savedState获取锁，恢复之前的同步状态
//        // 方法acquireQueued会返回是否中断。如果返回true的话，表示中断了，但此时中断标记位已被清除
//        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
//            interruptMode = REINTERRUPT;
//
//        // 如果是其它线程调用transferForSignal()方法将当前节点node加入到同步队列，其它线程会通过doSignal方法将当前节点（等待队列首节点）移除等待队列
//        // 如果是当前线程调用transferAfterCancelledWait()方法将节点node移到同步队列，这并不会将当前节点从等待队列中移除，这里将其移除
//        if (node.nextWaiter != null)
//            unlinkCancelledWaiters();
//
//        // 异常处理
//        //      1. THROW_IE：抛出中断异常
//        //      2. REINTERRUPT：重新设置中断标记位，这不会抛出异常
//        if (interruptMode != 0)
//            reportInterruptAfterWait(interruptMode);
//    }
//
//
//    ///////////////////////////////////////////////////////////////////
//
//    /**
//     * 更新节点的等待状态为0（INITIAL），并将节点加入同步队列。然后唤醒该节点的线程，使其进入一个自旋的过程，获取同步状态（即获取锁）
//     * <p>
//     * 如果节点被取消了返回false，否则成功返回true。
//     *
//     * @param node the node
//     * @return true if successfully transferred (else the node was
//     * cancelled before signal)
//     */
//    final boolean transferForSignal(Node node) {
//        /*
//            同步队列有一个小细节：
//                    新加入同步队列的节点的等待状态默认为0，然后后继节点会修改该节点的等待状态为SIGNAL（标记后继节点自己需要该节点来唤醒）
//                    具体参考：AQS acquireQueued 和 addWaiter 方法
//
//            如果修改等待状态失败，说明node已被取消（fullyRelease 在释放同步状态，即释放锁失败时会抛出异常，并设置等待状态为CANNELLED）
//         */
//        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
//            return false;
//
//        /*
//         * Splice onto queue and try to set waitStatus of predecessor to
//         * indicate that thread is (probably) waiting. If cancelled or
//         * attempt to set waitStatus fails, wake up to resync (in which
//         * case the waitStatus can be transiently and harmlessly wrong).
//         */
//        Node p = enq(node); // 将node加入同步队列，并返回node的前驱节点
//        int ws = p.waitStatus;
//        // 唤醒该节点node的线程，使其进入一个自旋的过程，获取同步状态（即获取锁）
//        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) // TODO
//            LockSupport.unpark(node.thread);
//        return true;
//    }
//
//    /**
//     * Removes and transfers nodes until hit non-cancelled one or
//     * null. Split out from signal in part to encourage compilers
//     * to inline the case of no waiters.
//     *
//     * @param first (non-null) the first node on condition queue
//     */
//    private void doSignal(Node first) { // 参数为等待队列的首节点
//        do {
//            /*
//                首先明确一点：
//                        await时，如果等待队列为null，则会初始化等待队列，此时等待队列中就一个新加入的节点，firstWaiter和lastWaiter都指向这一节点。
//
//                firstWaiter = first.nextWaiter 即移除等待队列中的首节点，
//                如果移除后，如果firstWaiter为null，说明等待队列中仅有的一个节点被移除了，lastWaiter也应该置null。
//             */
//
//            // firstWaiter指向当前节点的nextWaiter，同时将当前节点（等待队列的首节点）的nextWaiter指向null
//            // 即将其从等待队列中删除
//            if ((firstWaiter = first.nextWaiter) == null)
//                lastWaiter = null;
//            first.nextWaiter = null;
//        } while (
//            // 这里的判断很有意思，
//            // 如果当前节点被取消了 && 当前节点的nextWaiter存在，
//            // 则继续对firstWaiter.nextWaiter执行相同操作，直到等待队列中的一个节点被成功移到同步队列或者等待队列为空
//                !transferForSignal(first) &&
//                        (first = firstWaiter) != null
//                );
//    }
//
//    /**
//     * 将等待队列中等待时间最长的节点（如果存在的话）移动到同步队列。
//     *
//     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
//     *                                      returns {@code false}
//     */
//    public final void signal() {
//        if (!isHeldExclusively()) // 如果执行signal的方法的线程未获取锁，则抛出IllegalMonitorStateException
//            throw new IllegalMonitorStateException();
//        Node first = firstWaiter; // 等待队列的首节点
//        if (first != null)
//            doSignal(first);
//    }
}
