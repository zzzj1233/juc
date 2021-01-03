package com.zzzj.aqs;

import com.zzzj.aqs.MyReentrantLock.ConditionObj;

/**
 * @author Zzzj
 * @create 2020-12-30 13:58
 */
public class ConditionInterruptTest {

    public static void main(String[] args) throws InterruptedException {
        MyReentrantLock lock = new MyReentrantLock();
        ConditionObj condition = lock.newCondition();

        Thread thread = new Thread(() -> {
            try {
                lock.lock();
                condition.await();
            } catch (InterruptedException e) {
                System.out.println("被打断了~");
            } finally {
                lock.unlock();
                System.out.println("thread end ..." + Thread.interrupted());
            }
        });

        thread.start();

        Thread.sleep(200);

        lock.lock();

        thread.interrupt();

//        Thread.sleep(100);

        condition.signal();


        lock.unlock();

        System.out.println("end ...");
    }

}
