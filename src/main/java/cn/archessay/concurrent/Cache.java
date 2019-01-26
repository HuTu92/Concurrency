package cn.archessay.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by 刘春龙 on 2018/8/7.
 */
public class Cache {

    private static Map<String, Object> map = new HashMap<>();

    private static ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private static Lock r = rwl.readLock();
    private static Lock w = rwl.writeLock();

    // 获取一个key对应的value
    public static Object get(String key) {
        r.lock();
        try {
            return map.get(key);
        } finally {
            r.unlock();
        }
    }

    // 设置key对应的value，并返回旧的value
    public static Object put(String key, Object value) {
        w.lock();
        try {
            return map.put(key, value);
        } finally {
            w.unlock();
        }
    }

    // 清空所有的内容
    public static void clear() {
        w.lock();
        try {
            map.clear();
        } finally {
            w.unlock();
        }
    }

}
