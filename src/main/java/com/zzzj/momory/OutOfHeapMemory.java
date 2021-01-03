package com.zzzj.momory;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * @author Zzzj
 * @create 2020-12-30 12:55
 */
public class OutOfHeapMemory {

    private static final Unsafe THE_UNSAFE;

    static {
        try {
            final PrivilegedExceptionAction<Unsafe> action = () -> {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(null);
            };
            THE_UNSAFE = AccessController.doPrivileged(action);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }


    public static void main(String[] args) {
        // 和C一样
        long memoryAddress = THE_UNSAFE.allocateMemory(4);

        THE_UNSAFE.putInt(memoryAddress, 4);

        // 4
        System.out.println(THE_UNSAFE.getInt(memoryAddress));

        THE_UNSAFE.freeMemory(memoryAddress);
    }
}
