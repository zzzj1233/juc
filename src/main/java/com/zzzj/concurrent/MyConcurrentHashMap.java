package com.zzzj.concurrent;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 待完善
 * <ol>
 *     <li>链表长度 > 8 && table.length >= 64时 , 链表可以转红黑树</li>
 *     <li>CountCells数组可扩充</li>
 *     <li>transfer & resize</li>
 * </ol>
 *
 * @author Zzzj
 * @create 2021-01-06 17:56
 */
public class MyConcurrentHashMap<K, V> {

    private volatile AtomicReferenceArray<Node<K, V>> table;
    private volatile AtomicReferenceArray<CounterCell> counterCells;

    private AtomicInteger sizeCtl = new AtomicInteger(0);
    private AtomicLong baseCount = new AtomicLong(0);
    private AtomicInteger cellBusy = new AtomicInteger(0);

    private static final int DEFAULT_CAPACITY = 16;
    private int capacity;


    public V get(K key) {
        if (key == null) {
            throw new IllegalArgumentException("key can't be empty !");
        }
        if (table == null) {
            return null;
        }

        int hash = getHash(key);

        Node<K, V> node;

        if ((node = table.get(hash)) == null) {
            return null;
        }

        if (node.hash == hash && node.key.equals(key)) {
            return node.value;
        }

        // forwardingNode or tree
        if (node.hash < 0) {
            return node.find(hash, key);
        }

        // linked list
        Node<K, V> next = node;
        for (; ; ) {
            if (next.hash == hash && next.key.equals(key)) {
                return next.value;
            }
            if ((next = next.next) == null) {
                return null;
            }
        }
    }

    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("key can't be empty !");
        }

        while (table == null) {
            int sc;
            if ((sc = sizeCtl.get()) == -1) {
                Thread.yield();
            } else if (sizeCtl.compareAndSet(sc, -1)) {
                initTable();
            }
        }

        int hash = getHash(key);

        Node<K, V> node;
        if ((node = table.get(hash)) == null) {
            if (table.compareAndSet(hash, null, new Node<K, V>(key, value, hash))) {
                return;
            }
        }

        // forwarding node
        if (node.hash == Node.MOVED) {
            // help transfer
            return;
        }

        int count = 0;
        synchronized (node) {
            // linked list
            if (node.hash > 0) {
                Node<K, V> next = node;
                for (; ; count++) {
                    if (next.hash == hash && next.key.equals(key)) {
                        next.value = value;
                        return;
                    }
                    if (next.next != null) {
                        next = next.next;
                    } else {
                        next.next = new Node<>(key, value, hash);
                        break;
                    }
                }
            } else {
                // tree
            }
        }
        addCount(1, count);
    }

    private static int resizeStamp(int n) {
        return Integer.numberOfLeadingZeros(n) | (1 << (16 - 1));
    }

    private void addCount(int c, int check) {
        // 新增count
        long bc = baseCount.get();
        if (c > 0) {
            if (counterCells == null || !baseCount.compareAndSet(bc, bc + 1)) {
                int m;
                CounterCell counterCell;
                // 1. 如果counterCells == null,那么一定是cas起冲突导致进的if
                if (counterCells == null || (m = counterCells.length()) == 0 ||
                        (counterCell = counterCells.get((int) (Thread.currentThread().getId() % m))) == null ||
                        !counterCell.incr()
                ) {
                    fullAddCount(c);
                    // 不需要参与下面的扩容,因为此时一定发送了冲突
                    // 会有线程去进行扩容
                    return;
                }
            }
        }

        if (check >= 0) {
            long sum = getSize();
            int sc;
            int m;
            while (sum > (sc = sizeCtl.get()) && table != null) {
                // 每个stamp唯一对应一个table.length
                int stamp = resizeStamp(table.length());

                // 已经有线程在扩容了
                if (sc < 0) {

                } else {

                }
            }
        }
    }

    private void transfer() {

    }


    public long getSize() {
        if (counterCells == null) {
            return baseCount.get();
        }

        long ret = baseCount.get();
        for (int i = 0; i < counterCells.length(); i++) {
            CounterCell cell;
            if ((cell = counterCells.get(i)) != null) {
                ret += cell.count.get();
            }
        }
        return ret;
    }

    private void fullAddCount(int c) {
        int m;
        for (; ; ) {
            if (counterCells != null && (m = counterCells.length()) > 0) {
                int bucket = (int) (Thread.currentThread().getId() % m);
                CounterCell cell;
                if ((cell = counterCells.get(bucket)) == null && counterCells.compareAndSet(bucket, null, new CounterCell(c))) {
                    return;
                }
                if (cell.incr(c)) {
                    return;
                }
                // 竞争过于激烈
                // 此时应该扩容cellCounter数组
                if (cellBusy.get() == 0 && cellBusy.compareAndSet(0, 1)) {
                    // resize
                }
            } else {
                if (cellBusy.get() == 0 && counterCells == null && cellBusy.compareAndSet(0, 1)) {
                    CounterCell[] rs = new CounterCell[2];
                    int bucket = (int) (Thread.currentThread().getId() % 2);
                    rs[bucket] = new CounterCell(c);
                    if (counterCells == null) {
                        counterCells = new AtomicReferenceArray<>(rs);
                    }
                    cellBusy.set(0);
                    return;
                }
            }

            // 最后再尝试修改一次baseCount
            long bc = baseCount.get();
            if (baseCount.compareAndSet(bc, bc + c)) {
                return;
            }
        }
    }

    private int getHash(K key) {
        return key.hashCode() % table.length();
    }

    private void initTable() {
        capacity = DEFAULT_CAPACITY;
        Node<K, V>[] tab = new Node[capacity];
        sizeCtl.set((int) (capacity * 0.75));
        table = new AtomicReferenceArray<>(tab);
    }


    public static class CounterCell {
        volatile AtomicLong count;

        public CounterCell(long count) {
            this.count = new AtomicLong(count);
        }

        public boolean incr() {
            long c = this.count.get();
            return this.count.compareAndSet(c, c + 1);
        }

        public boolean incr(int count) {
            long c = this.count.get();
            return this.count.compareAndSet(c, c + count);
        }

    }

    private static class Node<K, V> {
        public K key;
        public V value;
        public int hash;
        public Node<K, V> next;

        public static final int MOVED = -1;

        public Node(K key, V value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }

        V find(int hash, K key) {
            throw new NotImplementedException();
        }
    }

    private static class ForwardingNode<K, V> extends Node<K, V> {

        private volatile AtomicReferenceArray<Node<K, V>> nextTable;

        public ForwardingNode(K key, V value, int hash) {
            super(key, value, hash);
        }

        @Override
        V find(int hash, K key) {
            // find nextTable
            return null;
        }
    }


}
