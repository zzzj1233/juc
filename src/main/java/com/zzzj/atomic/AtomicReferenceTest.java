package com.zzzj.atomic;

import com.zzzj.atomic.AtomicReferenceArrayTest.Student;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Zzzj
 * @create 2020-12-30 11:47
 */
public class AtomicReferenceTest {

    public static void main(String[] args) {
        AtomicReference<Student> reference = new AtomicReference<>();

        reference.compareAndSet(null, new Student("zzzj", 22));

        System.out.println(reference.get());
    }

}
