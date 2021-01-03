package com.zzzj.atomic;

import java.util.concurrent.atomic.AtomicIntegerArray;


/**
 * @author Zzzj
 * @create 2020-12-30 11:39
 */
public class AtomicIntArrayTest {


    public static void main(String[] args) {
        int[] intArr = {1, 2, 34};
        AtomicIntegerArray at = new AtomicIntegerArray(intArr);
        if (at.compareAndSet(1, 2, 22)) {
            // 22
            System.out.println(at.get(1));
            // 2,不会改变原数组
            System.out.println(intArr[1]);
        }
    }

}
