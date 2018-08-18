package com.github.hutu92.code.analysis;

/**
 * Created by liuchunlong on 2018/8/18.
 */
public class Join {

    /**
     * 等待当前线程死亡（执行结束）。
     * <p>
     * 调用此方法的行为与调用join(0)完全相同。
     *
     * @throws InterruptedException 如果有任何线程中断了当前线程。
     *                              抛出此异常时，将清除当前线程的中断状态。
     */
    public final void join() throws InterruptedException {
        join(0);
    }

    /**
     * 等待此线程死亡的时间最多为millis毫秒。超时0意味着永远等待。
     * <p>
     * 当满足this.isAlive条件时，会循环调用this.wait。
     * <p>
     * 当一个线程终止时，将调用this.notifyAll方法（该方法是在JVM底层调用的，jdk没有提供）。
     * <p>
     * 建议应用程序不要在Thread实例上使用wait，notify或notifyAll。
     *
     * @param millis 等待的时间，以毫秒为单位
     * @throws IllegalArgumentException 如果millis的值为负数抛出该异常
     * @throws InterruptedException     如果有任何线程中断了当前线程。
     *                                  抛出此异常时，将清除当前线程的中断状态。
     */
    public final synchronized void join(long millis)
            throws InterruptedException {
        long base = System.currentTimeMillis();
        long now = 0;

        if (millis < 0) { // millis的值为负数抛出IllegalArgumentException异常
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) { // 超时0意味着永远等待
            while (isAlive()) {
                wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) { // 等待超时，退出
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
    }

    /**
     * Tests if this thread is alive. A thread is alive if it has
     * been started and has not yet died.
     *
     * @return  <code>true</code> if this thread is alive;
     *          <code>false</code> otherwise.
     */
    public final native boolean isAlive();
}
