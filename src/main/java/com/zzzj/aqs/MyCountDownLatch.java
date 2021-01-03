package com.zzzj.aqs;

/**
 * @author Zzzj
 * @create 2020-12-29 21:47
 */
public class MyCountDownLatch {

    private final Sync sync;

    public MyCountDownLatch(int count) {
        sync = new Sync(count);
    }

    public void await() {
        sync.acquireShared(1);
    }

    public void countDown() {
        sync.releaseShared(1);
    }

    private static class Sync extends MyAQS {

        public Sync(int count) {
            state.set(count);
        }

        @Override
        public int tryAcquireShared(int release) {
            // 1 -> 需要传播
            // -1 -> 失败
            return state.get() == 0 ? 1 : -1;
        }

        @Override
        public boolean tryReleaseShared(int release) {
            int c = state.get();
            if (c == 0) {
                return false;
            }
            for (; ; ) {
                int nextC = c - release;
                if (state.compareAndSet(c, nextC)) {
                    return nextC == 0;
                }
            }
        }

    }

    public static void main(String[] args) {
        MyCountDownLatch countDownLatch = new MyCountDownLatch(5);

        new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(300);
                    countDownLatch.countDown();
                    System.out.println("count down ...");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            countDownLatch.await();
            System.out.println("t2 end ...");
        }).start();

        countDownLatch.await();

        System.out.println("main end ...");
    }


}
