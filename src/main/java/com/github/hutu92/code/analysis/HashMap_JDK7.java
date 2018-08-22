package com.github.hutu92.code.analysis;

/**
 * Created by 刘春龙 on 2018/8/10.
 */
public class HashMap_JDK7 {

    private static int roundUpToPowerOf2(int number) {
        // assert number >= 0 : "number must be non-negative";
        // 如果number大于等于最大容量，则返回MAXIMUM_CAPACITY
        // 否则，如果number大于1，则返回大于或等于number的最接近number的二次幂
        // 否则，返回1
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
    }

    /**
     * Inflates the table.
     */
    private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        // capacity为大于或等于toSize的最接近toSize的二次幂
        int capacity = roundUpToPowerOf2(toSize);
        // threshold = capacity * loadFactor
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Entry[capacity];
        initHashSeedAsNeeded(capacity); // TODO
    }

    /**
     * 将当前table中的所有entry传输到新的table。
     */
    void transfer(Entry[] newTable, boolean rehash) {
        int newCapacity = newTable.length;
        // 循环当前table中的所有链表
        for (Entry<K,V> e : table) {
            while(null != e) { // e为链表中的当前entry
                Entry<K,V> next = e.next; // next为链表中的e的下一个entry
                if (rehash) {
                    // key为null，hash为0，否则重新计算hash
                    e.hash = null == e.key ? 0 : hash(e.key);
                }
                // 重新计算bucket index
                int i = indexFor(e.hash, newCapacity);
                // 添加到新table的相应的bucket链表的头部
                e.next = newTable[i];
                newTable[i] = e;
                // 继续操作当前链表中的下一个entry
                e = next;
            }
        }
    }

    /**
     * 将此map的内容重新hash，放入具有更大容量的新数组中。
     * <p>
     * 当此map中的key数量达到其阈值时，将自动调用此方法。
     * <p>
     * 如果当前容量为MAXIMUM_CAPACITY，则此方法不会调整map大小，但会将阈值设置为Integer.MAX_VALUE。这可以防止将来再次被调用。
     *
     * @param newCapacity
     */
    void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;
        // 如果当前容量为MAXIMUM_CAPACITY，则此方法不会调整map大小，但会将阈值设置为Integer.MAX_VALUE。这可以防止将来再次被调用。
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        Entry[] newTable = new Entry[newCapacity];
        transfer(newTable, initHashSeedAsNeeded(newCapacity)); // TODO
        table = newTable;
        threshold = (int) Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    /**
     * 检索对象哈希码并将补充哈希函数应用于结果哈希，以防止质量差的哈希函数。
     * 这很关键，因为HashMap使用2的幂的长度哈希表，否则会遇到hashCodes的冲突。
     *
     * 注意：null key始终映射到哈希0，因此bucket index为0。
     */
    final int hash(Object k) {
        int h = hashSeed;
        if (0 != h && k instanceof String) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * 与addEntry类似，此版本在创建作为Map构造或“伪构造”（克隆，反序列化）一部分的entry时使用。
     * 此版本无需担心调整表的大小。
     * <p>
     * 子类重写此方法以改变HashMap(Map)，clone和readObject的行为。
     */
    void createEntry(int hash, K key, V value, int bucketIndex) {
        Entry<K, V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<>(hash, key, value, e); // 链表头部插入新的entry
        size++;
    }

    /**
     * 返回哈希码的bucket index，即table数组的下标
     *
     * @param h 哈希码
     * @param length map的容量（也即table的大小）
     * @return
     */
    static int indexFor(int h, int length) { // TODO
        // assert Integer.bitCount(length) == 1 : "length must be a non-zero power of 2";
        return h & (length-1);
    }

    /**
     * 将指定键，值和哈希码的新的entry添加到指定bucket。
     * 并在适当时候（当map中实际存储的 key-value 键值对的个数大于阈值，并且发生哈希冲突时），此方法会调整table的大小，将map的容量扩大2倍。
     * <p>
     * 子类可以通过重写此方法以改变put方法的行为。
     */
    void addEntry(int hash, K key, V value, int bucketIndex) {
        if ((size >= threshold) && (null != table[bucketIndex])) { // 当size（map中实际存储的 key-value 键值对的个数）超过临界阈值threshold，并且即将发生哈希冲突时进行扩容
            // 将map的容量扩大2倍
            resize(2 * table.length);
            hash = (null != key) ? hash(key) : 0; // resize会重新索引，所以这里要基于hashSeed，重新计算当前key的hash
            bucketIndex = indexFor(hash, table.length); // resize会重新索引，所以这里要重新计算bucket index
        }

        createEntry(hash, key, value, bucketIndex);
    }

    /**
     * 每当调用put(k,v)覆盖其key已经存在于HashMap中的entry的值时，就会调用此方法。
     */
    void recordAccess(HashMap_JDK7<K, V> m) {
    }

    /**
     * index为0的bucket的链表中key为null的entry存在，覆盖index为0的bucket的链表中key为null的entry的value值
     * 否则新增一个entry
     */
    private V putForNullKey(V value) {
        for (Entry<K, V> e = table[0]; e != null; e = e.next) {
            // index为0的bucket的链表中key为null的entry存在，
            // 则覆盖该entry的value值，并返回旧值
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this); // HashMap提供了空实现，子类重写该方法
                return oldValue;
            }
        }
        // index为0的bucket的链表中key为null的entry不存在，则新增一个entry
        modCount++; // map结构修改次数加1
        addEntry(0, null, value, 0);
        return null;
    }

    public V put(K key, V value) {
        // 如果table数组为空数组{}，进行数组填充（为table分配实际内存空间），
        // 入参为threshold，此时threshold为initialCapacity 默认是1<<4(pow(2,4)=16)
        if (table == EMPTY_TABLE) {
            // capacity = 大于等于threshold，且为2的n次方（Math.power(2, n)）并与toSize最相近的一个值
            // threshold = capacity * loadFactor
            // 初始化table大小为capacity
            inflateTable(threshold);
        }
        // 如果key为null，存储位置为table[0]或table[0]的冲突链上
        if (key == null)
            return putForNullKey(value);
        int hash = hash(key);// 计算key的哈希码，确保散列均匀
        int i = indexFor(hash, table.length);// 获取在table中的bucket index
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            // index为i的bucket的链表中满足如下条件的entry存在，
            // 则覆盖该entry的value值，并返回旧值
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }
        modCount++;// 保证并发访问时，若HashMap内部结构发生变化，快速响应失败
        addEntry(hash, key, value, i);// 新增一个entry
        return null;
    }


    /////////////////////////////////////////////

    private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null)
                return e.value;
        }
        return null;
    }

    final Entry<K,V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }
        // key为null，则hash为0，否则根据key计算hash
        int hash = (key == null) ? 0 : hash(key);
        // 根据hash和table的大小，计算出bucket index，进而找到对应bucket上的链表
        // 然后遍历链表上的entry，直到找到符合条件的entry，返回其value
        for (Entry<K,V> e = table[indexFor(hash, table.length)];
             e != null;
             e = e.next) {
            Object k;
            if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                return e;
        }
        return null;
    }

    public V get(Object key) {
        if (key == null)
            return getForNullKey();
        Entry<K,V> entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }
}
