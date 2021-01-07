package com.zzzj.concurrent;

import com.zzzj.concurrent.MyReentrantLock.ConditionObj;
import com.zzzj.struct.MinHeap;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Zzzj
 * @create 2021-01-02 20:12
 */
public class MySchedulerThreadPool extends MyExecutor {


    public MySchedulerThreadPool(Integer corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE, 0, TimeUnit.SECONDS, new MyWorkingDelayQueue());
    }

    public void schedule(Runnable runnable, long initDelay, TimeUnit unit) {
        long time = unit.toNanos(initDelay) + System.nanoTime();
        MyWorkingTask task = new MyWorkingTask(runnable, time);
        super.blockingQueue.add(task);
        this.ensureStartThread();
    }

    public void scheduleAtFixedRate(Runnable runnable, long initDelay, long delay, TimeUnit unit) {
        long time = unit.toNanos(initDelay) + System.nanoTime();
        MyWorkingTask task = new MyWorkingTask(runnable, time, unit.toNanos(delay));
        super.blockingQueue.add(task);
        this.ensureStartThread();
    }

    public void scheduleAtFixedDelay(Runnable runnable, long initDelay, long delay, TimeUnit unit) {
        long time = unit.toNanos(initDelay) + System.nanoTime();
        MyWorkingTask task = new MyWorkingTask(runnable, time, -unit.toNanos(delay));
        super.blockingQueue.add(task);
        this.ensureStartThread();
    }

    private void ensureStartThread() {
        addProcessor(null, true);
    }

    private static class MyWorkingTask implements Comparable<MyWorkingTask>, Runnable {

        public Runnable runnable;

        public long time;

        public long period;

        public boolean invokeOnce;

        public MyWorkingTask(Runnable runnable, long time, long period) {
            this.runnable = runnable;
            this.time = time;
            this.period = period;
        }

        public MyWorkingTask(Runnable runnable, long time) {
            this.runnable = runnable;
            this.time = time;
            this.invokeOnce = true;
        }

        @Override
        public int compareTo(MyWorkingTask o) {
            if (o == this) {
                return 0;
            }

            if (this.time < o.time) {
                return -1;
            } else if (this.time == o.time) {
                return 0;
            } else {
                return 1;
            }
        }

        @Override
        public void run() {
            this.runnable.run();
            this.time = this.period >= 0 ? this.time + this.period : System.nanoTime() + (-this.period);
        }
    }

    private static class MyWorkingDelayQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {

        private MinHeap<MyWorkingTask> minHeap;

        private MyReentrantLock reentrantLock;

        private ConditionObj conditionObj;

        public MyWorkingDelayQueue() {
            this.minHeap = new MinHeap<>();
            this.reentrantLock = new MyReentrantLock();
            this.conditionObj = this.reentrantLock.newCondition();
        }

        @Override
        public Iterator<Runnable> iterator() {
            return null;
        }

        @Override
        public int size() {
            return minHeap.data.size();
        }

        @Override
        public boolean offer(Runnable runnable) {
            reentrantLock.lock();
            MyWorkingTask task = (MyWorkingTask) runnable;
            try {
                minHeap.add(task);

                // findMax不可能返回null
                MyWorkingTask max = minHeap.findMax();

                if (max == task) {
                    this.conditionObj.signalAll();
                }

            } finally {
                reentrantLock.unlock();
            }
            return true;
        }

        @Override
        public void put(Runnable runnable) throws InterruptedException {

        }

        @Override
        public boolean offer(Runnable runnable, long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public Runnable take() throws InterruptedException {
            try {
                reentrantLock.lock();
                for (; ; ) {
                    MyWorkingTask max = minHeap.findMax();

                    if (max == null) {
                        conditionObj.await();
                        continue;
                    }

                    long time = max.time;

                    if (max.invokeOnce) {
                        // sift down
                        minHeap.extractMax();
                    }

                    for (; ; ) {
                        long invokeDelay = time - System.nanoTime();
                        if (invokeDelay <= 0) {
                            conditionObj.signalAll();
                            return max;
                        } else {
                            conditionObj.await(invokeDelay, TimeUnit.NANOSECONDS);
                        }
                    }

                }
            } finally {
                reentrantLock.unlock();
            }
        }

        @Override
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return null;
        }

        @Override
        public int remainingCapacity() {
            return 0;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c) {
            return 0;
        }

        @Override
        public int drainTo(Collection<? super Runnable> c, int maxElements) {
            return 0;
        }

        @Override
        public MyWorkingTask poll() {
            return null;
        }

        @Override
        public MyWorkingTask peek() {
            return null;
        }

    }

    public static void main(String[] args) throws InterruptedException {
        MySchedulerThreadPool pool = new MySchedulerThreadPool(2);

        Runtime.getRuntime().addShutdownHook(new Thread(pool::shutDown));

        pool.schedule(() -> {
            System.out.println("hello world~");
        }, 2000, TimeUnit.MILLISECONDS);

        pool.scheduleAtFixedDelay(() -> {
            System.out.println("hello world~");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

    }
}
