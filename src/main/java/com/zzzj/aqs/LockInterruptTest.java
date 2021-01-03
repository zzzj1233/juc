package com.zzzj.aqs;

/**
 * @author Zzzj
 * @create 2020-12-30 13:45
 */
public class LockInterruptTest {

    public static void main(String[] args) {
        MyReentrantLock lock = new MyReentrantLock();

        lock.lock();

        Thread t = new Thread(() -> {
            lock.lock();
            System.out.println("t end ...");
            System.out.println(Thread.interrupted());
            lock.unlock();
        });

        t.start();

        t.interrupt();

        lock.unlock();

        System.out.println("end ...");
    }
}
