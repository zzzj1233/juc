package com.zzzj.distributed;

import org.apache.curator.framework.CuratorFramework;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Zzzj
 * @create 2021-01-31 17:46
 */
public class ZkUtils {

    public static List<String> getSortedChildren(CuratorFramework client, String path, Predicate<String> filter) throws Exception {
        List<String> children = client.getChildren()
                .forPath(path);

        Map<String, String> map = children.stream().filter(filter).collect(Collectors.toMap(s -> s.substring(s.length() - 10), o -> o));

        return new ArrayList<>(new TreeMap<>(map).values());
    }

}
