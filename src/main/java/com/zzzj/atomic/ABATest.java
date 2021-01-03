package com.zzzj.atomic;

import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * @author Zzzj
 * @create 2020-12-30 12:52
 */
public class ABATest {
    public static void main(String[] args) {
        AtomicStampedReference<Integer> reference = new AtomicStampedReference<Integer>(0, 0);

        reference.compareAndSet(0, 1, 0, 1);
        reference.compareAndSet(1, 0, 1, 2);

        if (!reference.compareAndSet(0, 1, 0, 1)) {
            System.out.println("ABA happened");
        }

    }
}
