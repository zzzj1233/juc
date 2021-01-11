package com.zzzj.concurrent;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Zzzj
 * @create 2021-01-11 16:57
 */
public class MyFutureTask<V> implements RunnableFuture<V> {

    private final int NEW = 1;
    private final int RUNNING = 2;
    private final int COMPLETE = 3;
    private final int EXCEPTIONAL = 4;
    private final int CANCELLED = 5;
    private final int INTERRUPTING = 6;
    private final int INTERRUPTED = 7;

    private final Callable<V> callable;
    private final AtomicInteger state = new AtomicInteger(NEW);
    private Thread thread;
    private V result;
    private Throwable err;
    private volatile Waiter firstWaiter;
    private volatile Waiter lastWaiter;

    // for support cas
    private static Unsafe THE_UNSAFE;
    private static long resultOffset;
    private static long firstWaiterOffset;
    private static long lastWaiterOffset;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
            resultOffset = THE_UNSAFE.objectFieldOffset
                    (MyReentrantLock.class.getDeclaredField("result"));
            firstWaiterOffset = THE_UNSAFE.objectFieldOffset
                    (MyReentrantLock.class.getDeclaredField("firstWaiter"));
            lastWaiterOffset = THE_UNSAFE.objectFieldOffset
                    (MyReentrantLock.class.getDeclaredField("lastWaiter"));
        } catch (Exception e) {
            // ignore
        }
    }

    private boolean compareAndSetFirstWaiter(Waiter except, Waiter update) {
        return THE_UNSAFE.compareAndSwapObject(this, firstWaiterOffset, except, update);
    }

    private boolean compareAndSetLastWaiter(Waiter except, Waiter update) {
        return THE_UNSAFE.compareAndSwapObject(this, lastWaiterOffset, except, update);
    }

    public MyFutureTask(Callable<V> callable) {
        assert callable != null;
        this.callable = callable;
    }


    @Override
    public V get() throws InterruptedException, ExecutionException {
        doGet(-1);
        return null;
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        assert timeout > 0 && unit != null;
        doGet(unit.toNanos(timeout));
        return null;
    }

    public void doGet(long timeOut) throws InterruptedException {
        boolean timeout = timeOut > 0;

        boolean queued = false;
        Waiter waiter = null;
        long deadLine = System.nanoTime() + timeOut;
        for (; ; ) {
            // 1. 如果线程在等待时被中断了,直接抛出异常
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            // ps : Doug Lea 多加了一个completing状态,目的就是在此处尽量不让线程去休眠,减少休眠->唤醒带来的开销

            // 2. 是否已经完成了?
            if (state.get() > RUNNING) {
                // 出队
                deQueue(null);
                return;
            }

            // 这个判断就是为了再走一次循环,尽量减少休眠
            if (waiter == null) {
                waiter = new Waiter(Thread.currentThread());
            }
            // 3. 是否入队了?
            else if (!queued) {
                enQueue(null);
                // 入队
                queued = true;
            }
            // 4. 是否超时了 ?
            else if (timeout && System.nanoTime() <= deadLine) {
                // 出队
                deQueue(null);
                return;
            }
            // 5. 阻塞线程
            else if (timeout) {
                LockSupport.parkNanos(timeOut);
            } else {
                LockSupport.park();
            }
        }


    }

    private void enQueue(Waiter waiter) {
        if (firstWaiter == null && compareAndSetFirstWaiter(null, waiter)) {
            lastWaiter = firstWaiter;
            firstWaiter.next = lastWaiter;
            return;
        }

        while (lastWaiter == null || firstWaiter.next == null) {
            Thread.yield();
        }


        for (; ; ) {
            Waiter last = lastWaiter;
            if (compareAndSetLastWaiter(last, waiter)) {
                last.next = waiter;
                return;
            }
        }
    }

    private void deQueue(Waiter waiter) {
        if (firstWaiter == null) {
            return;
        }

        if (waiter == null) {
            return;
        }

        waiter.thread = null;

        // 从 head -> next -> next ... -> lastWaiter 一直向下寻找

        retry:
        for (Waiter first = firstWaiter, next = first, previous = null; ; next = first.next) {
            // 当前节点是否需要出队?
            if (next.thread != null) {
                previous = next;
                continue;
            } else if (previous != null) {
                previous.next = next.next;
                // 我们只需要关心当前节点的上一个节点是否被移除队列了。如果是,那么这次并发修改肯定会带来问题。
                // 如果是上上个节点被移除队列了,那么不会带来任何问题
                // 因为我们只是修改上个节点的next指向
                if (previous.thread == null) {
                    continue retry;
                }
                break;
                // 如果当前遍历的waiter的thread == null && previous == null , 那么当前waiter一定就是首节点
            } else if (!compareAndSetFirstWaiter(next, next.next)) {
                continue retry;
            }
        }

    }

    @Override
    public void run() {
        if (!state.compareAndSet(NEW, RUNNING)) {
            return;
        }
        thread = Thread.currentThread();
        try {
            handleSuccessful(callable.call());
        } catch (Throwable throwable) {
            handleFailure(throwable);
        } finally {
            // 1. handleCancel
            if (!cancelled()) {
                // 2. call waiter
                callWaiter();
            }
            // 3. clear
            thread = null;
        }

    }

    private void handleSuccessful(V result) {
        // why does Doug Lea use cas in hear ?
        // because cancel(true)
        if (state.compareAndSet(RUNNING, CANCELLED)) {
            this.result = result;
        }
    }

    private void handleFailure(Throwable throwable) {
        if (state.compareAndSet(RUNNING, EXCEPTIONAL)) {
            this.result = result;
        }
    }

    private boolean cancelled() {
        // 正在打断线程,此时应该交出cpu的执行权
        while (state.get() == INTERRUPTING) {
            Thread.yield();
        }

        int c;
        return (c = state.get()) == INTERRUPTED || c == CANCELLED;
    }

    private void callWaiter() {

    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 1. 不处于运行状态,可能是new,complete,exceptional,都会取消失败
        if (!state.compareAndSet(RUNNING, mayInterruptIfRunning ? INTERRUPTING : CANCELLED)) {
            return false;
        }
        // 2. 是否需要打断线程 ?
        if (mayInterruptIfRunning) {
            thread.interrupt();
            // 已经打断过了
            state.set(INTERRUPTED);
        }

        // 3. 唤醒等待队列 -> may async ?
        callWaiter();

        return true;
    }

    @Override
    public boolean isCancelled() {
        return state.get() == CANCELLED;
    }

    @Override
    public boolean isDone() {
        int c;
        return (c = state.get()) == COMPLETE || c == EXCEPTIONAL;
    }


    private static class Waiter {
        public Thread thread;
        public Waiter next;
        private long nextOffset;


        public Waiter(Thread thread) {
            this.thread = thread;
            try {
                nextOffset = THE_UNSAFE.objectFieldOffset
                        (Waiter.class.getDeclaredField("next"));
            } catch (NoSuchFieldException e) {

            }
        }

        public boolean compareAndSetNext(Waiter except, Waiter next) {
            return THE_UNSAFE.compareAndSwapObject(this, nextOffset, except, next);
        }
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        //        FutureTask<String> futureTask = new FutureTask<String>(() -> {
//            Thread.sleep(2000);
//            return "hello world~";
//        });
//
//        Thread thread = new Thread(futureTask);
//
//        thread.start();
//
//        Thread.sleep(100);
//
//        thread.interrupt();
//
//        System.out.println(futureTask.get());
    }

}
