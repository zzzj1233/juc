package com.zzzj.concurrent;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Zzzj
 * @create 2021-01-01 18:10
 */
public class MyExecutor {

    protected final Integer corePoolSize;
    protected final Integer maxPoolSize;
    private final long timeout;
    private final TimeUnit timeUnit;
    protected final BlockingQueue<Runnable> blockingQueue;
    protected AtomicInteger runningState = new AtomicInteger(RUNNING);
    protected AtomicInteger workerCount = new AtomicInteger(0);
    private MyReentrantLock lock;
    private boolean coreThreadTimeout;
    private ThreadFactory threadFactory;
    private HashSet<Processor> pool;

    private static final int RUNNING = 0;
    private static final int SHUTDOWN = -1;
    private static final int STOP = 1;
    private static final int TIDYING = 2;
    private static final int TERMINATED = 3;

    public MyExecutor(Integer corePoolSize, Integer maxPoolSize, long timeout, TimeUnit timeUnit, BlockingQueue<Runnable> blockingQueue) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.blockingQueue = blockingQueue;
        this.lock = new MyReentrantLock();
        this.coreThreadTimeout = false;
        this.threadFactory = Executors.defaultThreadFactory();
        this.pool = new HashSet<>(corePoolSize);
    }

    public void allowCoreThreadTimeout() {
        this.coreThreadTimeout = true;
    }

    public void execute(Runnable command) {
        if (workerCount.get() < corePoolSize) {
            addProcessor(command, true);
        } else if (blockingQueue.offer(command)) {
            if (runningState.get() != RUNNING && blockingQueue.remove(command)) {
                // 执行拒绝策略
            } else if (workerCount.get() == 0) {
                addProcessor(command, false);
            }
        } else {
            if (!addProcessor(command, false)) {
                // 执行拒绝策略
            }
        }
    }

    protected boolean addProcessor(Runnable command, boolean core) {
        for (; ; ) {
            int wc = workerCount.get();

            if (wc >= (core ? corePoolSize : maxPoolSize)) {
                return false;
            }

            // cas修改workerCount
            if (!workerCount.compareAndSet(wc, wc + 1)) {
                continue;
            }

            try {
                this.lock.lock();

                if (runningState.get() == RUNNING || command == null) {
                    Processor processor = new Processor(command);
                    // 开启线程
                    processor.thread.start();
                    pool.add(processor);
                }
                return true;
            } finally {
                this.lock.unlock();
            }
        }
    }

    public void shutDown() {
        if (!runningState.compareAndSet(RUNNING, SHUTDOWN)) {
            throw new IllegalArgumentException("thread pool is not running !");
        }

        lock.lock();
        // 打断所有正在等待任务的线程
        try {
            for (Processor processor : pool) {
                if (!processor.isRunning) {
                    processor.thread.interrupt();
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void shutDownNow() {

        if (runningState.get() == STOP) {
            throw new IllegalArgumentException("thread pool is stop !");
        }

        try {
            lock.lock();

            if (runningState.get() == STOP) {
                return;
            }

            if (!runningState.compareAndSet(runningState.get(), STOP)) {
                return;
            }

            // 打断所有线程
            for (Processor processor : pool) {
                processor.thread.interrupt();
            }

        } finally {
            lock.unlock();
        }

    }

    private void runWorker(Processor processor) {
        Runnable task = processor.command;
        processor.command = null;
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                processor.isRunning = true;
                try {
                    task.run();
                } finally {
                    task = null;
                    processor.isRunning = false;
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(processor, completedAbruptly);
        }
    }

    private void processWorkerExit(Processor processor, boolean completedAbruptly) {
        // 减少工作线程数
        int wc = workerCount.get();
        while (!workerCount.compareAndSet(wc, wc - 1)) {
        }
        pool.remove(processor);
    }

    private Runnable getTask() {

        boolean timed = coreThreadTimeout || workerCount.get() >= corePoolSize;
        for (; ; ) {
            if (runningState.get() != RUNNING) {
                return null;
            }
            try {
                Runnable task = timed ? blockingQueue.poll(timeout, timeUnit) : blockingQueue.take();
                return task;
            } catch (InterruptedException e) {
                // 等待任务的时候被中断了
                if (runningState.get() == RUNNING) {
                    Thread.interrupted();
                    continue;
                }
                return null;
            }
        }
    }


    protected class Processor implements Runnable {
        public Runnable command;

        public Thread thread;

        public boolean isRunning;

        private Processor(Runnable command) {
            this.command = command;
            this.thread = threadFactory.newThread(this);
        }

        public void run() {
            runWorker(this);
        }

    }


    public static void main(String[] args) throws InterruptedException {

        MyExecutor myExecutor = new MyExecutor(5, 10, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(5));

        for (int i = 0; i < 50; i++) {
            myExecutor.execute(() -> {
                String name = Thread.currentThread().getName();
                Random random = new Random();
                System.out.println(name + " start ...");
                try {
                    Thread.sleep(random.nextInt(2000));
                } catch (InterruptedException e) {
                    System.out.println("被打断了~ break ");
                    return;
                }
                System.out.println(name + " end ... > " + Thread.interrupted());
            });
        }
        Thread.sleep(500);

        myExecutor.shutDown();

        System.out.println("shutdown ... ");
    }

}
