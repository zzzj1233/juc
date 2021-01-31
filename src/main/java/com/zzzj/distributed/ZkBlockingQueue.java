package com.zzzj.distributed;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/**
 * @author Zzzj
 * @create 2021-01-31 19:17
 */
public class ZkBlockingQueue {

    private final String queuePath;

    private ZkLock lock;

    private ZkLock.ZkCondition notFull;

    private ZkLock.ZkCondition notEmpty;

    private int maxSize;

    private CuratorFramework client;

    private String lockPath;

    public ZkBlockingQueue(CuratorFramework client, String lockPath, String queuePath, int maxSize) throws Exception {
        this.client = client;
        this.lockPath = lockPath;
        this.lock = new ZkLock(client, lockPath);
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
        this.maxSize = maxSize;
        this.queuePath = queuePath;
        this.checkQueueExists();
    }

    private void checkQueueExists() throws Exception {
        if (client.checkExists().forPath(queuePath) == null) {

            client.create()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(queuePath);
        }
    }

    public int getSize() throws Exception {
        return ZkUtils.getSortedChildren(client, queuePath, s -> s.contains("queue_")).size();
    }

    public void offer(String value) throws Exception {
        try {
            lock.lock();

            while (getSize() >= maxSize) {
                notFull.await();
            }

            // 唤醒后新增元素
            client.create()
                    .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
                    .forPath(queuePath + "/" + "element_", value.getBytes(StandardCharsets.UTF_8));

            notEmpty.signalAll();

        } finally {
            lock.unlock();
        }
    }

    public String take() throws Exception {
        try {
            lock.lock();

            List<String> elements = null;

            while ((elements = ZkUtils.getSortedChildren(client, queuePath, s -> s.contains("element_"))).isEmpty()) {
                notEmpty.await();
            }

            // 唤醒后删除第一个元素
            String first = elements.get(0);

            byte[] bytes = client.getData()
                    .forPath(queuePath + "/" + first);

            client.delete()
                    .forPath(queuePath + "/" + first);

            notFull.signalAll();

            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        CuratorFramework client = CuratorFrameworkFactory
                .newClient("localhost:2181", 20000, 2000, new ExponentialBackoffRetry(1000, 3));

        client.start();
        client.blockUntilConnected();

        System.out.println("connected...");

        ZkBlockingQueue queue = new ZkBlockingQueue(client, "/lock2", "/queue1", 10);

        if (args.length == 0) {

            for (; ; ) {
                System.out.println("take : " + queue.take());
            }

        } else {
            Scanner scanner = new Scanner(System.in);

            for (; ; ) {
                String str = scanner.nextLine();
                queue.offer(str);
                System.out.println("offer : " + str);
            }
        }


    }

}
