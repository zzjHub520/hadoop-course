package cn.itcast.zookeeper_api;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ZooKeeperAPITest {
    //watch机制
    @Test
    public void watchZnode() throws Exception {
        //1: 定制一个重试策略
        /*
         * param1: 重试的间隔时间
         * param2: 重试的最大次数
         * */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 1);

        //2: 获取一个客户端对象
        /*
         * param1: 要连接的zookeeper服务器列表
         * param2: 会话的超时时间
         * param3: 链接超时时间
         * param4: 重试策略
         * */
        String connectionStr = "hadoop102:2181,hadoop103:2181,hadoop104:2181";
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionStr, 8000, 8000, retryPolicy);

        //3: 开启客户端
        client.start();

        //4: 创建一个treecache对象，指定要监控的节点路径
        TreeCache treeCache = new TreeCache(client, "/hello3");

        //5 自定义一个监听器
        treeCache.getListenable().addListener(new TreeCacheListener() {
            @Override
            public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent treeCacheEvent) throws Exception {
                ChildData data = treeCacheEvent.getData();
                if (data != null){
                    switch (treeCacheEvent.getType()){
                        case NODE_ADDED:
                            System.out.println("监控到有新增节点");
                            break;
                        case NODE_REMOVED:
                            System.out.println("监控到有节点被移除");
                            break;
                        case NODE_UPDATED:
                            System.out.println("监控到有节点被更新");
                            break;
                        default:
                            break;
                    }
                }

            }
        });

        //开始监听
        treeCache.start();
        Thread.sleep(1000000000);
    }


    //临时节点
    @Test
    public void createTmpZnode() throws Exception {
        //1: 定制一个重试策略
        /*
         * param1: 重试的间隔时间
         * param2: 重试的最大次数
         * */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 1);

        //2: 获取一个客户端对象
        /*
         * param1: 要连接的zookeeper服务器列表
         * param2: 会话的超时时间
         * param3: 链接超时时间
         * param4: 重试策略
         * */
        String connectionStr = "hadoop102:2181,hadoop103:2181,hadoop104:2181";
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionStr, 8000, 8000, retryPolicy);

        //3: 开启客户端
        client.start();

        //4: 创建节点
        client.create().creatingParentContainersIfNeeded().withMode(CreateMode.EPHEMERAL).forPath("/hello3", "world".getBytes(StandardCharsets.UTF_8));

        Thread.sleep(5000);
        //5: 关闭客户端
        client.close();
    }

    //永久节点
    @Test
    public void createZnode() throws Exception {
        //1: 定制一个重试策略
        /*
         * param1: 重试的间隔时间
         * param2: 重试的最大次数
         * */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 1);

        //2: 获取一个客户端对象
        /*
         * param1: 要连接的zookeeper服务器列表
         * param2: 会话的超时时间
         * param3: 链接超时时间
         * param4: 重试策略
         * */
        String connectionStr = "hadoop102:2181,hadoop103:2181,hadoop104:2181";
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionStr, 8000, 8000, retryPolicy);

        //3: 开启客户端
        client.start();

        //4: 创建节点
        client.create().creatingParentContainersIfNeeded().withMode(CreateMode.PERSISTENT).forPath("/hello2", "world".getBytes(StandardCharsets.UTF_8));

        //5: 关闭客户端
        client.close();
    }
}
