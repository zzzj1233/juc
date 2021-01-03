package com.zzzj.aqs;

import com.sun.istack.internal.Nullable;
import com.zzzj.aqs.MyReentrantLock.ConditionObj;

import java.util.concurrent.BrokenBarrierException;

/**
 * @author Zzzj
 * @create 2020-12-29 23:03
 */
public class MyCyclicBarrier {

    private final int achieveCount;
    private int count;
    @Nullable
    private Runnable command;

    private MyReentrantLock lock;
    private ConditionObj conditionObj;

    public MyCyclicBarrier(int achieveCount, @Nullable Runnable command) {
        this.achieveCount = achieveCount;
        this.count = achieveCount;
        this.command = command;

        this.lock = new MyReentrantLock();
        this.conditionObj = lock.newCondition();
    }


    public void await() throws BrokenBarrierException, InterruptedException {
        try {
            lock.lock();

            this.count--;

            // 人满了
            if (this.count == 0) {
                if (command != null) {
                    command.run();
                }
                return;
            } else {
                try {
                    conditionObj.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            lock.unlock();
        }

    }

    public static void main(String[] args) {
        MyCyclicBarrier myCyclicBarrier = new MyCyclicBarrier(5, null);

        for (int i = 0; i < 5; i++) {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    System.out.println(Thread.currentThread().getName() + "  before");
                    myCyclicBarrier.await();
                    System.out.println(Thread.currentThread().getName() + "  after");
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }).start();
        }


    }

}
