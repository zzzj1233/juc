package com.zzzj.concurrent;

import sun.misc.Unsafe;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Zzzj
 * @create 2020-12-29 21:31
 */
public abstract class MyAQS {

    private Node tail;
    private Node head;

    private static final Unsafe THE_UNSAFE;
    private static long tailOffset;
    private static long headOffset;
    public AtomicInteger state = new AtomicInteger(0);
    protected Thread currentThread;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
            tailOffset = THE_UNSAFE.objectFieldOffset
                    (MyAQS.class.getDeclaredField("tail"));
            headOffset = THE_UNSAFE.objectFieldOffset
                    (MyAQS.class.getDeclaredField("head"));
        } catch (Exception e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

    private boolean compareAndSetTail(Node expect, Node update) {
        return THE_UNSAFE.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private boolean compareAndSetHead(Node update) {
        return THE_UNSAFE.compareAndSwapObject(this, headOffset, null, update);
    }

    private static class Node {
        public Node next;
        public Node previous;
        public int waitState;
        public Thread thread;
        public Node nextWaiter;

        public static final int SHARED = -2;
        public static final int SIGNAL = -1;
        public static final int CANCELED = 1;

        public boolean shared() {
            return this.waitState == SHARED;
        }
    }

    protected int tryAcquireShared(int release) {
        throw new NotImplementedException();
    }

    protected boolean tryReleaseShared(int release) {
        throw new NotImplementedException();
    }

    public void acquireShared(int release) {

        if (tryAcquireShared(release) < 0) {
            doAcquireShared(release);
        }

    }

    public void acquire(int require) {
        if (!tryAcquire(require)) {

        }
    }

    public void release(int releases) {

    }

    protected boolean tryAcquire(int require) {
        throw new NotImplementedException();
    }

    protected boolean tryRelease() {
        throw new NotImplementedException();
    }

    public void releaseShared(int release) {

        if (tryReleaseShared(release)) {
            doReleaseShared();
        }

    }

    private void doAcquireShared(int release) {
        // 1. 入队
        Node node = addWaiter(Node.SHARED);

        // 2. 抢锁 -> 阻塞
        for (; ; ) {
            Node previous = node.previous;
            if (previous == head) {

                // 成功
                int shared = tryAcquireShared(release);
                if (shared >= 0) {
                    setHeadAndPropagate(node, shared);
                    return;
                }

            }
            LockSupport.park(this);
        }
    }

    // > 0 = 需要传播
    private void setHeadAndPropagate(Node node, int shared) {
        head = node;
        head.thread = null;
        head.previous = null;

        if (node.next != null && node.next.shared()) {
            doReleaseShared();
        }
    }

    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;

            if (h != null) {
                Node next = h.next;
                if (next != null && next.thread != null) {
                    LockSupport.unpark(next.thread);
                }
            }

            if (h == head) {
                return;
            }
        }
    }

    private Node addWaiter(int state) {
        Node node = new Node();
        node.thread = Thread.currentThread();
        node.waitState = state;

        for (; ; ) {
            if (tail == null) {
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            } else {
                Node temp = tail;
                if (compareAndSetTail(temp, node)) {
                    temp.next = node;
                    node.previous = temp;
                    return node;
                }
            }
        }
    }

}
