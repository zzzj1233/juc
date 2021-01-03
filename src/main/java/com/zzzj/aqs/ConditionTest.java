package com.zzzj.aqs;

/**
 * @author Zzzj
 * @create 2020-12-29 13:59
 */
public class ConditionTest {

    public static void main(String[] args) throws InterruptedException {
        MyQueue queue = new MyQueue(10);

        new Thread(() -> {
            for (; ; ) {
                try {
                    queue.put(1);
                    Thread.sleep(200);
                    System.out.println("==================放了一个");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            for (; ; ) {
                try {
                    queue.take();
                    Thread.sleep(500);
                    System.out.println("取了一个========");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        for (; ; ) {
            queue.take();
            Thread.sleep(500);
            System.out.println("取了一个");
        }



    }

}
