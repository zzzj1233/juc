package com.zzzj.distributed;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Zzzj
 * @create 2021-01-31 17:06
 */
public class ZkLock {

    private final CuratorFramework client;

    final String lockPath;

    private ConcurrentHashMap<Thread, LockFlag> map;

    public ZkLock(CuratorFramework client, String lockPath) throws Exception {
        this.client = client;
        this.lockPath = lockPath;
        this.map = new ConcurrentHashMap<>(16);
        if (!lockPathExists()) {
            createLockPath();
        }
    }

    private boolean lockPathExists() throws Exception {
        return client.checkExists()
                .forPath(lockPath) != null;
    }

    private void createLockPath() throws Exception {
        try {
            client
                    .create()
                    .creatingParentsIfNeeded()
                    .forPath(lockPath);
        } catch (NodeExistsException e) {
            // ignore
        }
    }

    public void lock() throws Exception {
        lock(1);
    }

    public void lock(int acquire) throws Exception {
        map.putIfAbsent(Thread.currentThread(), new LockFlag(acquire));
        LockFlag lockFlag = map.get(Thread.currentThread());

        if (lockFlag.counter.get() > 1) {
            // reentry
            lockFlag.counter.incrementAndGet();
            return;
        }

        // first lock
        String path = client
                .create()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(lockPath + "/" + "node_")
                .substring(lockPath.length() + 1);

        lockFlag.path = path;

        // 检查是否是第一个
        for (; ; ) {
            List<String> children = ZkUtils.getSortedChildren(client, lockPath, s -> s.contains("node_"));

            int index = children.indexOf(lockFlag.path);

            if (children.size() == 1 || index == 0) {
                Stat stat = client.checkExists().forPath(lockPath);
                int version = stat.getVersion();
                try {
                    client.setData().withVersion(version).forPath(lockPath, path.getBytes(StandardCharsets.UTF_8));
                    return;
                } catch (BadVersionException e) {
                    continue;
                }
            }

            String previous = children.get(index - 1);

            // watch previous
            final Thread thread = Thread.currentThread();
            try {
                client
                        .getData()
                        .usingWatcher((CuratorWatcher) event -> {
                            if (event.getType() == EventType.NodeDeleted) {
                                LockSupport.unpark(thread);
                            }
                        })
                        .forPath(lockPath + "/" + previous);
                break;
            } catch (NoNodeException e) {
                // watch failure
                continue;
            }
        }

        while (true) {
            LockSupport.park();

            // 无视被打断唤醒的情况
            if (Thread.interrupted()) {
                continue;
            }
            client.setData().forPath(lockPath, path.getBytes(StandardCharsets.UTF_8));
            break;
        }
    }

    private static class LockInfo {
        // 当前获取锁的节点
        public String path;

        public String awaitNodePath;
    }

    private static class LockFlag {
        public final AtomicInteger counter;
        public String path;

        public LockFlag() {
            this.counter = new AtomicInteger(1);
        }

        public LockFlag(int count) {
            this.counter = new AtomicInteger(count);
        }
    }

    private class DeleteWatcher implements CuratorWatcher {

        @Override
        public void process(WatchedEvent event) throws Exception {

            if (event.getType() == EventType.NodeDeleted) {

            }

        }

    }

    public void lockInterruptibly() throws InterruptedException {

    }

    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    public void unlock() throws Exception {
        // not null
        LockFlag lockFlag = map.get(Thread.currentThread());

        if (lockFlag.counter.get() > 1) {
            // reentry
            lockFlag.counter.decrementAndGet();
            return;
        }

        if (lockFlag.counter.get() > 1) {
            lockFlag.counter.decrementAndGet();
            return;
        }

        // 删除当前节点
        client.delete().forPath(lockPath + "/" + lockFlag.path);
        map.remove(Thread.currentThread());
    }

    public ZkCondition newCondition() {
        return new ZkCondition();
    }

    public class ZkCondition {

        public void await() throws Exception {
            // 1. 当前线程必须持有锁
            LockFlag lockFlag = checkHoldLock();

            // 2. 放锁
            client.delete().forPath(lockPath + "/" + lockFlag.path);

            // 3. 进入等待队列,等待被唤醒
            String path = client.create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(lockPath + "/await_");

            Thread thread = Thread.currentThread();
            client.getData().usingWatcher((CuratorWatcher) event -> {
                if (event.getType() == EventType.NodeDeleted) {
                    LockSupport.unpark(thread);
                }
            }).forPath(path);

            LockSupport.park();

            // 4. 重新抢锁
            lock(lockFlag.counter.get());
        }


        private LockFlag checkHoldLock() throws Exception {
            LockFlag lockFlag = map.get(Thread.currentThread());

            // 1. 检查内存
            if (lockFlag == null) {
                throw new IllegalStateException("thread must hold lock !");
            }

            // 2 检查zk的basePath节点
            byte[] bytes = client.getData().forPath(lockPath);

            if (!new String(bytes).equals(lockFlag.path)) {
                throw new IllegalStateException("thread must hold lock !");
            }

            return lockFlag;
        }

        public void signal() throws Exception {
            // 1. 当前线程必须持有锁
            checkHoldLock();

            // 2. 从await节点上随机唤醒一个
            List<String> children = ZkUtils.getSortedChildren(client, lockPath, s -> s.contains("await_"));

            if (children.isEmpty()) {
                return;
            }

            String randomNode = children.get(new Random().nextInt(children.size()));

            client.delete()
                    .forPath(lockPath + "/" + randomNode);
        }

        public void signalAll() throws Exception {
            checkHoldLock();

            List<String> children = ZkUtils.getSortedChildren(client, lockPath, s -> s.contains("await_"));

            if (children.isEmpty()) {
                return;
            }

            for (String child : children) {
                client.delete()
                        .forPath(lockPath + "/" + child);
            }
        }

    }

    public static void main(String[] args) throws Exception {
        CuratorFramework client = CuratorFrameworkFactory
                .newClient("localhost:2181", 20000, 2000, new ExponentialBackoffRetry(1000, 3));

        client.start();
        client.blockUntilConnected();

        ZkLock lock = new ZkLock(client, "/lock1");

        lock.lock();

        System.out.println("get lock ~ ");

        System.in.read();

        lock.unlock();

        System.out.println("end ...");
    }

}
