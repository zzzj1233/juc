package com.zzzj.aqs;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Zzzj
 * @create 2020-12-28 16:56
 */
public class MyRWLock {
    private Node head;
    private Node tail;
    private AtomicInteger writeState = new AtomicInteger(0);
    private AtomicInteger readState = new AtomicInteger(0);
    private Thread currentThread;

    private static final Unsafe THE_UNSAFE;
    private static long tailOffset;
    private static long headOffset;

    private WriteLock writeLock;
    private ReadLock readLock;

    public MyRWLock() {
        this.writeLock = new WriteLock();
        this.readLock = new ReadLock();
    }

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
            tailOffset = THE_UNSAFE.objectFieldOffset
                    (MyRWLock.class.getDeclaredField("tail"));
            headOffset = THE_UNSAFE.objectFieldOffset
                    (MyRWLock.class.getDeclaredField("head"));
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
        public boolean shared;
        public Thread thread;

        public Node(Node next, Node previous, boolean shared, Thread thread) {
            this.next = next;
            this.previous = previous;
            this.shared = shared;
            this.thread = thread;
        }
    }

    Node addWaiter(boolean shared) {
        Node node = new Node(null, null, shared, Thread.currentThread());

        for (; ; ) {
            if (tail == null) {
                Node head = new Node(null, null, false, null);
                if (compareAndSetHead(head)) {
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


    private class WriteLock {

        public void lock() {
            if (!tryAcquire()) {
                // 1. 添加到队列
                // 2. 阻塞
                acquireQueued(addWaiter(false));
            }
        }

        private void acquireQueued(Node node) {
            for (; ; ) {
                Node previous = node.previous;

                if (previous == head) {
                    if (tryAcquire()) {
                        head = node;
                        node.thread = null;
                        return;
                    }
                }

                LockSupport.park(this);
            }
        }

        private boolean tryAcquire() {
            int c = writeState.get();

            if (readState.get() != 0) {
                return false;
            } else if (c == 0) {
                if (writeState.compareAndSet(0, 1)) {
                    currentThread = Thread.currentThread();
                    return true;
                }
            } else if (Thread.currentThread() == currentThread) {
                writeState.set(c + 1);
                return true;
            }
            return false;
        }

        public void unlock() {
            if (Thread.currentThread() != currentThread) {
                throw new RuntimeException("unpack");
            }

            int c = writeState.get();

            if (c - 1 == 0) {
                if (head != null && head.next != null) {
                    LockSupport.unpark(head.next.thread);
                }
                currentThread = null;
                writeState.set(0);
            } else {
                writeState.set(c - 1);
            }
        }

    }

    private class ReadLock {

        private volatile Thread firstReader;
        private volatile int readCount;
        private ThreadLocal<Integer> rcHolder = ThreadLocal.withInitial(() -> 0);

        public void lock() {

            if (!tryAcquire()) {
                // 入队和阻塞
                acquireQueued(addWaiter(true));
            }

        }

        private void acquireQueued(Node node) {

            for (; ; ) {
                Node previous = node.previous;
                if (previous == head) {
                    if (tryAcquire()) {
                        head = node;
                        node.thread = null;
                        return;
                    }
                }
                LockSupport.park(this);
            }

        }

        public void unlock() {
            Thread thread = Thread.currentThread();

            if (firstReader == thread) {
                if (--readCount == 0) {
                    firstReader = null;
                }
            } else {
                Integer holdCount = rcHolder.get();
                if (holdCount == 0) {
                    throw new RuntimeException("read lock unpack");
                }
                Integer sub = holdCount - 1;

                if (sub == 0) {
                    rcHolder.remove();
                } else {
                    rcHolder.set(sub);
                }

            }

            for (; ; ) {
                int rc = readState.get();
                int except = rc - 1;
                if (readState.compareAndSet(rc, except)) {

                    if (except == 0) {
                        // 唤醒
                        if (head != null && head.next != null) {
                            if (head.next.shared) {
                                throw new RuntimeException("head's next is shared ? ");
                            }
                            LockSupport.unpark(head.next.thread);
                        }
                    }

                    return;
                }
            }

        }

        private boolean tryAcquire() {
            Thread thread = Thread.currentThread();
            // 写锁被占,并且不是自己占有的
            if (writeState.get() != 0 && thread != currentThread) {
                return false;
            }
            // 下一个等待元素是写锁
            if (head != null && head.next != null && head.next.thread != null && !head.next.shared) {
                return false;
            }

            int c = readState.get();

            if (readState.compareAndSet(c, c + 1)) {

                if (firstReader == null) {
                    firstReader = thread;
                    readCount = 1;
                } else if (firstReader == thread) {
                    readCount++;
                } else {
                    rcHolder.set(rcHolder.get() + 1);
                }

                return true;
            }

            return fullTryAcquireShared();
        }


        private boolean fullTryAcquireShared() {
            Thread thread = Thread.currentThread();

            for (; ; ) {
                // 写锁被占,并且不是自己占有的
                if (writeState.get() != 0 && thread != currentThread) {
                    return false;
                }
                // 下一个等待元素是写锁
                if (head != null && head.next != null && head.next.thread != null && !head.next.shared) {
                    return false;
                }

                int c = readState.get();

                if (readState.compareAndSet(c, c + 1)) {
                    if (firstReader == null) {
                        firstReader = thread;
                        readCount = 1;
                    } else if (firstReader == thread) {
                        readCount++;
                    } else {
                        rcHolder.set(rcHolder.get() + 1);
                    }
                    return true;
                }
            }
        }

    }

    public static void main(String[] args) throws InterruptedException {
        MyRWLock lock = new MyRWLock();

        new Thread(() -> {
            System.out.println("before read lock () ... ");
            lock.writeLock.lock();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            lock.writeLock.unlock();
            System.out.println("after read lock () ... ");
        }).start();

//        new Thread(() -> {
//            try {
//                Thread.sleep(200);
//                System.out.println("t2 before read lock () ... ");
//                lock.readLock.lock();
//                lock.readLock.unlock();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            System.out.println("t2 after read lock () ... ");
//        }).start();


        Thread.sleep(500);

        System.out.println("main 等待锁");
        lock.readLock.lock();
        lock.readLock.unlock();
        System.out.println("main 释放锁");
    }

}
