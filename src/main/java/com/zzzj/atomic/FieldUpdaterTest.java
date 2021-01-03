package com.zzzj.atomic;

import com.zzzj.atomic.AtomicReferenceArrayTest.Student;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author Zzzj
 * @create 2020-12-30 11:48
 */
public class FieldUpdaterTest {
    public static void main(String[] args) {
        // 1. 字段必须是public的
        // 2. 字段必须是volatile修饰的
        AtomicReferenceFieldUpdater<Student, Integer> ageUpdater = AtomicReferenceFieldUpdater.newUpdater(Student.class, Integer.class, "age");

        Student zzzj = new Student("zzzj", 22);

        for (int i = 0; i < 10; i++) {

            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    if (ageUpdater.compareAndSet(zzzj, 22, 23)) {
                        System.out.println(Thread.currentThread().getName() + "修改成功");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }).start();
        }


    }
}
