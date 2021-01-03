package com.zzzj.aqs;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Zzzj
 * @create 2020-12-31 16:51
 */
public class Test {

    public static void main(String[] args) {

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);

        executor.scheduleAtFixedRate(() -> {
            System.out.println("scheduleAtFixedRate");
        }, 1000, 2500, TimeUnit.MILLISECONDS);

        executor.scheduleWithFixedDelay(() -> {
            System.out.println("scheduleAtFixedRate");
        }, 1000, 2500, TimeUnit.MILLISECONDS);
    }

}
