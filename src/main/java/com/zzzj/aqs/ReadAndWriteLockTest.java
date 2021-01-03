package com.zzzj.aqs;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Zzzj
 * @create 2020-12-27 19:40
 */

public class ReadAndWriteLockTest {

    public static void main(String[] args) throws InterruptedException {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        new Thread(() -> {
            System.out.println("before read lock () ... ");
            lock.readLock().lock();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            lock.readLock().unlock();
            System.out.println("after read lock () ... ");
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("t2 before read lock () ... ");
                lock.writeLock().lock();
                lock.writeLock().unlock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t2 after read lock () ... ");
        }).start();

        new Thread(() -> {
            try {
                Thread.sleep(700);
                System.out.println("t3 before read lock () ... ");
                lock.writeLock().lock();
                lock.writeLock().unlock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("t3 after read lock () ... ");
        }).start();


        Thread.sleep(500);

        System.out.println("main 等待锁");
        lock.readLock().lock();
        lock.readLock().unlock();
        System.out.println("main 释放锁");
    }
}

