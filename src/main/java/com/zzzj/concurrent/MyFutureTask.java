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

    // for support cas
    private static Unsafe THE_UNSAFE;
    private static long resultOffset;
    private static long firstWaiterOffset;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
            resultOffset = THE_UNSAFE.objectFieldOffset
                    (MyFutureTask.class.getDeclaredField("result"));
            firstWaiterOffset = THE_UNSAFE.objectFieldOffset
                    (MyFutureTask.class.getDeclaredField("firstWaiter"));
        } catch (Exception e) {
            // ignore
        }
    }

    private boolean compareAndSetFirstWaiter(Waiter except, Waiter update) {
        return THE_UNSAFE.compareAndSwapObject(this, firstWaiterOffset, except, update);
    }


    public MyFutureTask(Callable<V> callable) {
        assert callable != null;
        this.callable = callable;
    }


    @Override
    public V get() throws InterruptedException, ExecutionException {
        doGet(-1);

        // 正常执行
        if (state.get() == COMPLETE) {
            return result;
        }

        // 执行时抛出了异常
        if (state.get() == EXCEPTIONAL) {
            throw new ExecutionException(err);
        }

        // 被取消了
        if (state.get() >= CANCELLED) {
            throw new CancellationException();
        }

        // 不可能执行到这
        throw new RuntimeException("error !");
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        assert timeout > 0 && unit != null;
        doGet(unit.toNanos(timeout));
        // 正常执行
        if (state.get() == COMPLETE) {
            return result;
        }

        // 执行时抛出了异常
        if (state.get() == EXCEPTIONAL) {
            throw new ExecutionException(err);
        }

        // 被取消了
        if (state.get() >= CANCELLED) {
            throw new CancellationException();
        }

        // 超时了,还是没有获取到结果
        throw new TimeoutException();
    }

    public void doGet(long timeOut) throws InterruptedException {
        boolean timeout = timeOut > 0;

        boolean queued = false;
        Waiter waiter = null;
        long deadLine = System.nanoTime() + timeOut;
        for (; ; ) {
            // 1. 如果线程在等待时被中断了,直接抛出异常
            if (Thread.interrupted()) {
                if (waiter != null) {
                    deQueue(waiter);
                }
                throw new InterruptedException();
            }

            // ps : Doug Lea 多加了一个completing状态,目的就是在此处尽量不让线程去休眠,减少休眠->唤醒带来的开销

            // 2. 是否已经完成了?
            if (state.get() > RUNNING) {
                // 出队
                deQueue(waiter);
                return;
            }

            // 这个判断就是为了再走一次循环,尽量减少休眠
            if (waiter == null) {
                waiter = new Waiter(Thread.currentThread());
            }
            // 3. 是否入队了?
            else if (!queued) {
                enQueue(waiter);
                // 入队
                queued = true;
            }
            // 4. 是否超时了 ?
            else if (timeout && System.nanoTime() <= deadLine) {
                // 出队
                deQueue(waiter);
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
            return;
        }

        for (; ; ) {
            Waiter first = firstWaiter;
            waiter.next = first;
            if (compareAndSetFirstWaiter(first, waiter)) {
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
        for (Waiter first = firstWaiter, next = first, previous = null; next != null; next = first.next) {
            // 当前节点是否需要出队?
            if (next.thread != null) {
                previous = next;
            } else if (previous != null) {
                // 修改指向
                previous.next = next.next;
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
        if (state.compareAndSet(RUNNING, COMPLETE)) {
            this.result = result;
        }
    }

    private void handleFailure(Throwable throwable) {
        if (state.compareAndSet(RUNNING, EXCEPTIONAL)) {
            this.err = throwable;
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
        if (firstWaiter == null) {
            return;
        }

        Waiter first = firstWaiter;

        if (!compareAndSetFirstWaiter(first, null)) {
            return;
        }

        while (first != null) {
            Thread thread = first.thread;
            if (thread != null) {
                LockSupport.unpark(thread);
            }
            first.thread = null;
            first = first.next;
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 1. 不处于运行状态,可能是new,complete,exceptional,都会取消失败
        if (!state.compareAndSet(RUNNING, mayInterruptIfRunning ? INTERRUPTING : CANCELLED)) {
            return false;
        }
        // 2. 是否需要打断线程 ?
        if (mayInterruptIfRunning) {
            Thread.currentThread().interrupt();
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

    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {
        MyFutureTask<String> futureTask = new MyFutureTask<String>(() -> {
            Thread.sleep(2000);
            return "hello world~";
        });

        Thread thread = new Thread(futureTask);

        thread.start();

        Thread.sleep(100);

//        futureTask.cancel(false);
        System.out.println(futureTask.get(1000, TimeUnit.MILLISECONDS));
    }

}
