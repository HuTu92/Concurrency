package cn.archessay.concurrent;

/**
 * Created by liuchunlong on 2018/9/2.
 */
public class ThreadLocal {

    private static java.lang.ThreadLocal<Long> longLocal = new java.lang.ThreadLocal<>();
    private static java.lang.ThreadLocal<String> stringLocal = new java.lang.ThreadLocal<>();

    public void set() {
        longLocal.set(Thread.currentThread().getId());
        stringLocal.set(Thread.currentThread().getName());
    }

    public long getLong() {
        return longLocal.get();
    }

    public String getString() {
        return stringLocal.get();
    }


    /*
        1     main
        12     Thread-0
        1     main
     */
    public static void main(String[] args) throws InterruptedException {
        final ThreadLocal test = new ThreadLocal();

        test.set();
        System.out.print(test.getLong() + "     ");
        System.out.println(test.getString());


        Thread t = new Thread(){
            public void run() {
                test.set();
                System.out.print(test.getLong() + "     ");
                System.out.println(test.getString());
            };
        };
        t.start();
        t.join();

        System.out.print(test.getLong() + "     ");
        System.out.println(test.getString());
    }

}
