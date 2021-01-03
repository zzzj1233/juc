package com.zzzj.aqs;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Zzzj
 * @create 2020-12-17 22:24
 */
public class ConcurrentHashMapNotSafe {

    public static void main(String[] args) throws InterruptedException {

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<String, Integer>(32);

        map.put("zzzj", 0);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                Integer val = map.get("zzzj");
//                val += 1;
//                map.put("zzzj", val);

                while (!map.replace("zzzj", val, val + 1)) {
                    val = map.get("zzzj");
                }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                Integer val = map.get("zzzj");
//                val += 1;
//                map.put("zzzj", val);


                while (!map.replace("zzzj", val, val + 1)) {
                    val = map.get("zzzj");
                }

            }
        });

        t1.start();
        t2.start();


        t1.join();
        t2.join();

        // 每次的值都不一样
        // 因为concurrentHashMap只保证读取和插入时线程安全
        System.out.println(map.get("zzzj"));
    }

}
