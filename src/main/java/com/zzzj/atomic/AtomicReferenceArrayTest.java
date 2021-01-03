package com.zzzj.atomic;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * @author Zzzj
 * @create 2020-12-30 11:42
 */
public class AtomicReferenceArrayTest {

    public static class Student {
        public String name;
        public volatile Integer age;

        public Student(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "Student{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }

    public static void main(String[] args) {
        Student zzzj = new Student("zzzj", 22);
        Student dl = new Student("dl", 22);
        Student[] arr = {zzzj, dl};

        AtomicReferenceArray<Student> referenceArray = new AtomicReferenceArray<>(arr);

        referenceArray.compareAndSet(0, zzzj, dl);

        // true
        assert referenceArray.get(0) == referenceArray.get(1);
    }

}
