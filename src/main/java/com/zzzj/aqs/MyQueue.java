package com.zzzj.aqs;

import com.zzzj.aqs.MyReentrantLock.ConditionObj;

/**
 * @author Zzzj
 * @create 2020-12-29 15:38
 */
public class MyQueue<T> {

    private MyReentrantLock lock;
    private int count;
    private T[] items;
    private ConditionObj notFull;
    private ConditionObj notEmpty;

    public MyQueue(int size) {
        this.lock = new MyReentrantLock();
        this.count = 0;
        this.items = (T[]) new Object[1];
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    public void put(T item) throws InterruptedException {
        try {
            lock.lock();
            while (count == items.length - 1) {
                notFull.await();
            }
            items[this.count] = item;
            this.count++;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        try {
            lock.lock();
            while (this.count == 0) {
                this.notEmpty.await();
            }
            T item = this.items[this.count];
            this.count--;
            this.notFull.signalAll();
            return item;
        } finally {
            lock.unlock();
        }
    }
}
