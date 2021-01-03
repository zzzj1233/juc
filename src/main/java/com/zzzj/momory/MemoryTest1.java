package com.zzzj.momory;

import org.openjdk.jol.info.ClassLayout;

/**
 * @author Zzzj
 * @create 2020-12-30 12:35
 */
public class MemoryTest1 {
    public byte a = 1;
    public int b = 1;
    public boolean c;

    public static void main(String[] args) {

        System.out.println(ClassLayout.parseInstance(new MemoryTest1()).toPrintable());

    }

}
