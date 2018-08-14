package com.github.hutu92;

/**
 * Created by 刘春龙 on 2018/8/14.
 */
public class HashMap_JDK8 {

    static final int TREEIFY_THRESHOLD = 8;

    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // Create a regular (non-tree) node
    Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
        return new Node<>(hash, key, value, next);
    }

    // Callbacks to allow LinkedHashMap post-actions
    void afterNodeAccess(Node<K,V> p) { }
    void afterNodeInsertion(boolean evict) { }

    /**
     * Implements Map.put and related methods
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent if true, don't change existing value
     * @param evict if false, the table is in creation mode.
     * @return previous value, or null if none
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab;
        Node<K,V> p; // 数组当前bucket中链表的头节点
        int n, i;
        // 如果 table 还未被初始化，那么初始化它
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        // 根据键的 hash 值找到该键对应到数组中的bucket
        // 如果为 null，那么说明此索引位置并没有被占用，创建一个新的Node保存到该索引位置
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        // 不为 null，说明此处已经被占用，只需要将构建一个节点插入到这个链表的尾部即可
        else {
            Node<K,V> e;
            K k; // 数组当前bucket中链表的头节点的key
            // 当前节点和将要插入的节点的 hash 和 key 相同，说明这是一次修改操作
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            // 如果 p 这个头节点是红黑树节点的话，以红黑树的插入形式进行插入
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value); // TODO
            // 遍历此条链表，将构建一个节点插入到该链表的尾部
            else {
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) { // 已遍历到了链表的尾部
                        p.next = newNode(hash, key, value, null); // 在尾部插入新的节点
                        // default, binCount >= 7
                        // 如果插入前链表长度大于等于 8 ，将链表裂变成红黑树
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash); // TODO
                        break;
                    }
                    // 遍历的过程中，如果发现与某个节点的 hash 和 key 相同，这依然是一次修改操作
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    // 继续遍历
                    p = e;
                }
            }
            // e 不是 null，说明当前的 put 操作是一次修改操作并且e指向的就是需要被修改的节点
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        // 如果添加后，数组容量达到阈值，进行扩容
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }


    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table; // 旧数组
        int oldCap = (oldTab == null) ? 0 : oldTab.length; // 旧数组长度，即容量
        int oldThr = threshold; // 旧数组的阈值
        int newCap, newThr = 0;
        // 说明旧数组已经被初始化完成了，此处需要给旧数组扩容
        if (oldCap > 0) {
            // 达到容量限定的极限将不再扩容
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 未达到极限，将数组容量扩大两倍，阈值也扩大两倍。
            //
            // 此外，如果数组容量扩容后大于极限，此时阈值不再有意义，没必要再扩大两倍，
            // 这时newThr为0，后面的逻辑会将其赋值为Integer.MAX_VALUE，标记不再触发扩容。
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                    oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        // 数组未初始化，但阈值不为 0，说明使用构造函数 HashMap(int initialCapacity, float loadFactor) 初始化的。
        //
        // 为什么不为0呢？
        //      上述提到 jdk 大神偷懒的事情就指的这，构造函数根据传入的容量计算出了一个合适的数组容量暂存在阈值中，这里直接拿来使用
        //
        // 但是此时，newThr为0
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        // 数组未初始化并且阈值也为0，说明使用无参构造函数 HashMap() 初始化的，一切都以默认值进行构造
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY; // 16
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY); // 16 * 0.75
        }
        // 上面有两处提到newThr为0，这里对其进行赋值
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr; // 设置新的阈值threshold

        // 这一部分代码结束后，无论是初始化数组还是扩容，总之，必需的数组容量和阈值都已经计算完成了。下面看后续的代码：

        // 根据新的容量初始化一个数组
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab; // 设置新的数组table
        // 旧数组不为 null，这次的 resize 是一次扩容行为
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                // 获取旧数组中索引为j的bucket链表的头节点
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    // 说明链表只有一个头节点，转移至新表
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    // 如果 e 是红黑树结点，红黑树分裂，转移至新表
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap); // TODO
                    // 这部分是将链表中的各个节点原序地转移至新表中，具体为什么会这么做，我们后续会详细说明
                    else { // preserve order
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            // 判断扩容后节点的索引是否会发生变化，
                            // 不变的节点组成一条新链；
                            // 改变的节点也组成一条新链；
                            if ((e.hash & oldCap) == 0) { // 等于0，节点的索引不变
                                if (loTail == null) // loTail 为 null，则初始化链表 loTail = loHead = e
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else { // 不等于0，节点的索引改变
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        // 索引不变的那条链表保存在数组的原索引上
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        // 索引改变的那条链表保存在数组的新索引上
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        // 不论你是扩容还是初始化，都可以返回 newTab
        return newTab;
    }

    /**
     * Tree version of putVal.
     */
    final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab,
                                   int h, K k, V v) {
        Class<?> kc = null;
        boolean searched = false;
        TreeNode<K,V> root = (parent != null) ? root() : this;
        for (TreeNode<K,V> p = root;;) {
            int dir, ph; K pk;
            if ((ph = p.hash) > h)
                dir = -1;
            else if (ph < h)
                dir = 1;
            else if ((pk = p.key) == k || (k != null && k.equals(pk)))
                return p;
            else if ((kc == null &&
                    (kc = comparableClassFor(k)) == null) ||
                    (dir = compareComparables(kc, k, pk)) == 0) {
                if (!searched) {
                    TreeNode<K,V> q, ch;
                    searched = true;
                    if (((ch = p.left) != null &&
                            (q = ch.find(h, k, kc)) != null) ||
                            ((ch = p.right) != null &&
                                    (q = ch.find(h, k, kc)) != null))
                        return q;
                }
                dir = tieBreakOrder(k, pk);
            }

            TreeNode<K,V> xp = p;
            if ((p = (dir <= 0) ? p.left : p.right) == null) {
                Node<K,V> xpn = xp.next;
                TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);
                if (dir <= 0)
                    xp.left = x;
                else
                    xp.right = x;
                xp.next = x;
                x.parent = x.prev = xp;
                if (xpn != null)
                    ((TreeNode<K,V>)xpn).prev = x;
                moveRootToFront(tab, balanceInsertion(root, x));
                return null;
            }
        }
    }
}
