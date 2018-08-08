package com.github.hutu92;

/**
 * Created by liuchunlong on 2018/8/7.
 */
public class Condition {

    /**
     * 从首节点遍历，删除取消的节点
     */
    private void unlinkCancelledWaiters() {
        Node t = firstWaiter; // 首节点
        Node trail = null;
        while (t != null) {
            Node next = t.nextWaiter;
            if (t.waitStatus != Node.CONDITION) {
                t.nextWaiter = null;
                if (trail == null)
                    firstWaiter = next;
                else
                    trail.nextWaiter = next;
                if (next == null)
                    lastWaiter = trail;
            }
            else
                trail = t;
            t = next;
        }
    }

    /**
     * 向等待队列中加入一个新的节点
     *
     * @return its new wait node
     */
    private Node addConditionWaiter() {
        // 等待队列的尾节点
        Node t = lastWaiter;
        if (t != null && t.waitStatus != Node.CONDITION) { // 尾节点不为null，且等待状态不为CONDITION，即为CANCELLED
            unlinkCancelledWaiters();
            t = lastWaiter; // 重新赋值尾节点
        }
        Node node = new Node(Thread.currentThread(), Node.CONDITION);
        if (t == null) // 如果等待队列的尾节点为null，说明等待队列中没有等待的线程，执行等待队列初始化。
            firstWaiter = node;
        else
            t.nextWaiter = node;
        lastWaiter = node;
        return node;
    }
}
