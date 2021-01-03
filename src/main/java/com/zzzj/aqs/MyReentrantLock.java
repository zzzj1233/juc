package com.zzzj.aqs;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Zzzj
 * @create 2020-12-26 18:44
 */
public class MyReentrantLock {

    private volatile Node head;
    private volatile Node tail;
    private volatile Thread currentThread;
    private AtomicInteger state = new AtomicInteger(0);

    private static final Unsafe THE_UNSAFE;
    private static long tailOffset;
    private static long headOffset;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
            tailOffset = THE_UNSAFE.objectFieldOffset
                    (MyReentrantLock.class.getDeclaredField("tail"));
            headOffset = THE_UNSAFE.objectFieldOffset
                    (MyReentrantLock.class.getDeclaredField("head"));
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
        public Thread thread;
        public Node next;
        public Node previous;
        public Node nextWaiter;
        public int waitState;

        public static final int CONDITION = -2;
        public static final int SIGNAL = -1;
        private static long waitOffset;

        static {
            try {
                waitOffset = THE_UNSAFE.objectFieldOffset(
                        Node.class.getDeclaredField("waitState")
                );
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

        public boolean compareAndSetWaitState(int except, int update) {
            return THE_UNSAFE.compareAndSwapInt(this, waitOffset, except, update);
        }

        public Node() {
            this.thread = Thread.currentThread();
        }

        public Node(int waitState) {
            this.thread = Thread.currentThread();
            this.waitState = waitState;
        }
    }

    public ConditionObj newCondition() {
        return new ConditionObj();
    }

    public class ConditionObj {

        private Node firstWaiter;
        private Node lastWaiter;

        private Node addConditionWaiter() {
            Node node = new Node(Node.CONDITION);
            if (firstWaiter == null) {
                firstWaiter = lastWaiter = node;
            } else {
                lastWaiter.nextWaiter = node;
                lastWaiter = node;
            }
            return node;
        }

        public void await() throws InterruptedException {
            // 1. 当前线程必须持有锁
            if (Thread.currentThread() != currentThread) {
                throw new RuntimeException(" await ");
            }

            // 2. 加入condition队列
            Node node = addConditionWaiter();
            // 3. 放锁
            int savedState = fullRelease();

            // 4. 等待唤醒
            int interruptMode = 0;

            while (!isInSyncQueue(node)) {
                LockSupport.park(this);
                interruptMode = checkInterrupted(node);
            }

            // 5. 重新抢锁
            acquireQueued(node, savedState);

            // 6. 出条件队列
            unlinkCancelledWaiters();

            // 7. 处理唤醒
            if (interruptMode == 1) {
                throw new InterruptedException();
            } else if (interruptMode == 2) {
                Thread.currentThread().interrupt();
            }

        }

        private int checkInterrupted(Node waiter) {
            if (Thread.interrupted()) {
                // 是否在signal前被唤醒了?
                if (waiter.compareAndSetWaitState(Node.CONDITION, Node.SIGNAL)) {
                    transferToSyncQueue(waiter);
                    return 1;
                }

                if (!isInSyncQueue(waiter)) {
                    Thread.yield();
                }

                return 2;
            } else {
                // 没有被打断,正常被唤醒了
                return 0;
            }
        }

        public void await(long delay, TimeUnit unit) throws InterruptedException {
            if (Thread.currentThread() != currentThread) {
                throw new RuntimeException(" await ");
            }

            Node waiter = addConditionWaiter();

            int savedRelease = fullRelease();

            long deadline = unit.toNanos(delay) + System.nanoTime();

            int interruptMode = 0;

            while (!isInSyncQueue(waiter)) {
                long timeout = deadline - System.nanoTime();

                // 等待完成了,如果还没有在sync队列中,那么直接放入sync队列
                if (timeout <= 0) {
                    // 必须使用cas,因为此时可能有别的线程正在signal
                    if (waiter.compareAndSetWaitState(Node.CONDITION, Node.SIGNAL)) {
                        transferToSyncQueue(waiter);
                        break;
                    }
                    continue;
                }

                LockSupport.parkNanos(timeout);

                interruptMode = checkInterrupted(waiter);
            }

            // 再次抢锁
            acquireQueued(waiter, savedRelease);

            // 将waiterQueue中state不是Condition的出队
            unlinkCancelledWaiters();

            if (interruptMode == 1) {
                throw new InterruptedException();
            } else if (interruptMode == 2) {
                Thread.interrupted();
            }
        }

        private void unlinkCancelledWaiters() {
            Node node = this.firstWaiter;

            Node tail = null;

            while (node != null) {

                Node nextWaiter = node.nextWaiter;

                if (node.waitState != Node.CONDITION) {
                    if (tail == null) {
                        firstWaiter = nextWaiter;
                    } else {
                        tail.nextWaiter = nextWaiter;
                    }
                } else {
                    tail = node;
                }

                node = nextWaiter;
            }
        }

        private boolean isInSyncQueue(Node node) {
            if (node.waitState == Node.CONDITION) {
                return false;
            }
            if (node.next != null) {
                return true;
            }
            // 从sync队列找

            Node previous = tail;

            while (previous != null) {
                if (previous == node) {
                    return true;
                }
                previous = previous.previous;
            }

            return false;
        }

        public void signal() {
            // 1. 当前线程必须持有锁
            if (Thread.currentThread() != currentThread) {
                throw new RuntimeException(" await ");
            }
            // 2. 等待队列是否为空
            if (firstWaiter == null) {
                return;
            }
            // 3. 把第一个元素转移到同步队列
            Node first = firstWaiter;
            firstWaiter = firstWaiter.nextWaiter;

            boolean condition = true;
            while (condition) {

                if (first.compareAndSetWaitState(Node.CONDITION, Node.SIGNAL)) {
                    transferToSyncQueue(first);
                    condition = false;
                }

                if (firstWaiter != null) {
                    first = firstWaiter;
                    firstWaiter = firstWaiter.nextWaiter;
                } else {
                    break;
                }

            }
        }

        private void transferToSyncQueue(Node node) {
            for (; ; ) {
                if (tail == null) {
                    tail = head = new Node(Node.SIGNAL);
                } else {
                    Node temp = tail;
                    if (compareAndSetTail(temp, node)) {
                        node.previous = temp;
                        temp.next = node;
                    }
                    return;
                }
            }
        }

        public void signalAll() {
            Node first = firstWaiter;

            if (first == null) {
                return;
            }

            firstWaiter = lastWaiter = null;

            if (tail == null) {
                tail = head = new Node(Node.SIGNAL);
            }

            while (first != null) {
                first.waitState = 0;
                first.previous = tail;
                tail.next = first;
                tail = first;
                first = first.nextWaiter;
            }

        }

    }

    public void lock() {
        if (!tryAcquire(1)) {
            // 入队
            Node node = addWaiter();
            // 阻塞
            if (acquireQueued(node, 1)) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Node addWaiter() {
        Node node = new Node();
        node.thread = Thread.currentThread();

        for (; ; ) {
            // init
            if (tail == null && compareAndSetHead(new Node())) {
                tail = head;
            } else {
                Node temp = tail;
                if (compareAndSetTail(temp, node)) {
                    node.previous = temp;
                    temp.next = node;
                    return node;
                }
            }
        }
    }

    private boolean acquireQueued(Node node, int acquire) {
        boolean interrupt = false;
        for (; ; ) {
            Node previous = node.previous;

            if (previous == head && tryAcquire(acquire)) {
                head.next = null;
                head = node;
                node.previous = null;
                node.thread = null;
                return interrupt;
            }

            LockSupport.park();

            if (Thread.interrupted()) {
                interrupt = true;
            }

        }

    }

    protected boolean tryAcquire(int acquire) {
        int c = state.get();

        if (c == 0) {
            if (state.compareAndSet(0, acquire)) {
                currentThread = Thread.currentThread();
                return true;
            }
        } else if (Thread.currentThread() == currentThread) {
            state.set(c + acquire);
            return true;
        }

        return false;
    }

    public void unlock() {
        if (Thread.currentThread() != currentThread) {
            throw new RuntimeException("unlock");
        }

        int s = state.get();

        if (s - 1 == 0) {
            currentThread = null;
            state.set(s - 1);
            if (head != null) {
                Node next = head.next;
                if (next != null) {
                    LockSupport.unpark(next.thread);
                }
            }
        } else {
            state.set(s - 1);
        }
    }

    private int fullRelease() {
        AtomicInteger state = this.state;
        Integer savedState = state.get();
        currentThread = null;
        state.set(0);
        if (head != null) {
            Node next = head.next;
            if (next != null) {
                LockSupport.unpark(head.next.thread);
            }
        }
        return savedState;
    }

    public static void main(String[] args) throws InterruptedException {
        MyReentrantLock lock = new MyReentrantLock();

        int sum[] = {0};
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100000; j++) {
                    lock.lock();
                    sum[0]++;
                    lock.unlock();
                }
            }).start();
        }

        Thread.sleep(1000);
        System.out.println(sum[0]);

    }

}
