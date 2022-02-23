前面用了五篇文章来介绍Hadoop的相关模块，理论学完还得操作一把才能加深理解。这一篇我会花相当长的时间从环境搭建开始，到怎么在使用Hadoop，逐步介绍Hadoop的使用。

本篇分这么几段内容：

1. 规划部署节点
2. 节点免密和网络配置
3. zookeeper分布式集群搭建
4. Hadoop分布式集群搭建
5. IDEA远程提交MapReduce任务到分布式集群

## 规划部署节点

HDFS高可用至少有两个NameNode（NN），副本存三份有 三个DataNode（DN）。Yarn高可用至少有两个Resource Manager（RM），计算向存储移动需要在每个DataNode上部署NodeManager（NM）。Zookeeper（ZK）三节点选主。JournalNode（JN）三节点才有过半成功。

基于以上考虑，规划部署节点如下：

| 主机     | IP            | NN   | RM   | ZKFC | DN   | NM   | JN   | ZK   |
| -------- | ------------- | ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| server01 | 192.168.0.111 | •    | •    | •    |      |      |      |      |
| server02 | 192.168.0.112 | •    | •    | •    |      |      |      |      |
| server03 | 192.168.0.113 |      |      |      | •    | •    | •    | •    |
| server04 | 192.168.0.114 |      |      |      | •    | •    | •    | •    |
| server05 | 192.168.0.115 |      |      |      | •    | •    | •    | •    |

注意：1.ZKFC与NameNode必须同节点才能选主。

​      2.DataNode与NodeManager必须同节点，体现计算向数据移动。

​      3.新建hadoop用户和组，以上软件全部用hadoop用户启动。

## 节点免密和网络配置

先要关闭五台机的防火墙，这一点非常重要。否则后面启动zookeeper后，zkServer.sh status不能正常查看zk状态，三个zk之间无法通讯。第二个Namenode也无法同步第一个Namenode的数据。

```
[root@server04 home]# systemctl stop firewalld
[root@server04 home]# systemctl disable firewalld
Removed symlink /etc/systemd/system/multi-user.target.wants/firewalld.service.
Removed symlink /etc/systemd/system/dbus-org.fedoraproject.FirewallD1.service.
```

按照规划修改/etc/hostname文件中的主机名。

在/etc/hosts文件中增加五台机的主机名解析。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
127.0.0.1   localhost localhost.localdomain localhost4 localhost4.localdomain4
::1         localhost localhost.localdomain localhost6 localhost6.localdomain6
192.168.0.111 server01
192.168.0.112 server02
192.168.0.113 server03
192.168.0.114 server04
192.168.0.115 server05
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

###  节点免密

由于server01、server02上可能会启动其他节点的脚本，所以server01和server02要对其他节点免密。

以server01为例，依次输入以下命令，将server01的公钥分发给其他节点，达到免密登录的目的。

注意一点，ssh-copy-id生成的authorized_keys权限是600，如果权限是644就没办法免密登录，这个文件权限要求很苛刻。

```
[hadoop@server01 ~]$ ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa
[hadoop@server01 ~]$ ssh-copy-id -i ~/.ssh/id_rsa.pub  hadoop@server01
[hadoop@server01 ~]$ ssh-copy-id -i ~/.ssh/id_rsa.pub  hadoop@server02
[hadoop@server01 ~]$ ssh-copy-id -i ~/.ssh/id_rsa.pub  hadoop@server03
[hadoop@server01 ~]$ ssh-copy-id -i ~/.ssh/id_rsa.pub  hadoop@server04
[hadoop@server01 ~]$ ssh-copy-id -i ~/.ssh/id_rsa.pub  hadoop@server05
```

## zookeeper分布式集群搭建

### 下载zookeeper

从官网上下载apache-zookeeper-3.5.8-bin.tar.gz包，注意不带bin的包里面是源码，不能启动QuorumPeerMain。

### 安装zookeeper

将apache-zookeeper-3.5.8-bin.tar.gz包解压到/usr目录下，把用户和组设置为hadoop。

在环境变量里增加

```
ZOOKEEPER_HOME=/usr/apache-zookeeper-3.5.8-bin
PATH=$PATH:$JAVA_HOME/bin:$ZOOKEEPER_HOME/bin
```

将/usr/apache-zookeeper-3.5.8-bin/conf/zoo_sample.cfg复制一份到相同目录，命令为zoo.cfg，这是启动zookeeper时要读取的配置文件。

以server03为例，修改zoo.cfg的内容。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server03 home]$ cat /usr/apache-zookeeper-3.5.8-bin/conf/zoo.cfg
# The number of milliseconds of each tick
tickTime=2000
# The number of ticks that the initial 
# synchronization phase can take
initLimit=10
# The number of ticks that can pass between 
# sending a request and getting an acknowledgement
syncLimit=5
# the directory where the snapshot is stored.
# do not use /tmp for storage, /tmp here is just 
# example sakes.
# 这里存放zookeeper的myid文件
dataDir=/opt/zookeeper/data
# the port at which the clients will connect
clientPort=2181
# the maximum number of client connections.
# increase this if you need to handle more clients
#maxClientCnxns=60
#
# Be sure to read the maintenance section of the 
# administrator guide before turning on autopurge.
#
# http://zookeeper.apache.org/doc/current/zookeeperAdmin.html#sc_maintenance
#
# The number of snapshots to retain in dataDir
#autopurge.snapRetainCount=3
# Purge task interval in hours
# Set to "0" to disable auto purge feature
#autopurge.purgeInterval=1

# server.x的数值与myid中的一致，后面接是zookeeper的三个节点。
# 2888端口是集群节点通讯使用
# 3888端口是选举leader使用
server.3=server03:2888:3888
server.4=server04:2888:3888
server.5=server05:2888:3888
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

在/opt/zookeeper/data目录下创建myid文件

```
[hadoop@server03 home]$ echo 3 > /opt/zookeeper/data/myid
```

至此，zookeeper搭建完毕，server04和server05的步骤一致。

在三台机器分别启动zookeeper。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server03 home]$ zkServer.sh start
[hadoop@server03 home]$ jps
3762 Jps
1594 QuorumPeerMain
[hadoop@server03 home]$ zkServer.sh status
/bin/java
ZooKeeper JMX enabled by default
Using config: /usr/apache-zookeeper-3.5.8-bin/bin/../conf/zoo.cfg
Client port found: 2181. Client address: localhost.
Mode: leader
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

server03被选为主节点了。

## Hadoop分布式集群搭建

 Hadoop基于Java开发，必须先安装jdk。本次实验使用的版本是jdk-11.0.8，安装完成后需要在环境变量里加入JAVA_HOME。

### 下载Hadoop

从官网下载hadoop-3.3.0.tar.gz，解压到/usr目录下，并将用户和组修改为hadoop。

### 配置Hadoop

官方网站上有详细的说明 [Apache Hadoop 3.3.0](https://hadoop.apache.org/docs/r3.3.0/)，把单节点的配置和高可用配置放在相应的文件下就行，这里我再梳理一遍。在此之前，需要修改/usr/hadoop-3.3.0/etc/hadoop/hadoop-env.sh，增加export JAVA_HOME=/usr/java/jdk-11.0.8，让hadoop运行命令时能调用jdk。

在环境变量里增加

```
HADOOP_HOME=/usr/hadoop-3.3.0
PATH=$PATH:$JAVA_HOME/bin:$ZOOKEEPER_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
```

\1. /usr/hadoop-3.3.0/etc/hadoop/core-site.xml

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
<configuration>
    <property>
      <name>fs.defaultFS</name>
      <value>hdfs://mycluster</value>
    </property>
    <property>
      <name>hadoop.tmp.dir</name>
      <value>/opt/hadoop/tmp</value>
    </property>
     <property>
       <name>ha.zookeeper.quorum</name>
       <value>server03:2181,server04:2181,server05:2181</value>
     </property>
</configuration>
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

2./usr/hadoop-3.3.0/etc/hadoop/hdfs-site.xml

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
<configuration>
    <property>
        <name>dfs.replication</name>
        <value>3</value>
    </property>
    <property>
      <name>dfs.nameservices</name>
      <value>mycluster</value>
    </property>
    <property>
      <name>dfs.ha.namenodes.mycluster</name>
      <value>nn1,nn2</value>
    </property>
    <property>
      <name>dfs.namenode.rpc-address.mycluster.nn1</name>
      <value>server01:8020</value>
    </property>
    <property>
      <name>dfs.namenode.rpc-address.mycluster.nn2</name>
      <value>server02:8020</value>
    </property>
    <property>
      <name>dfs.namenode.http-address.mycluster.nn1</name>
      <value>server01:9870</value>
    </property>
    <property>
      <name>dfs.namenode.http-address.mycluster.nn2</name>
      <value>server02:9870</value>
    </property>
    <property>
      <name>dfs.namenode.shared.edits.dir</name>
      <value>qjournal://server03:8485;server04:8485;server05:8485/mycluster</value>
    </property>
    <property>
      <name>dfs.client.failover.proxy.provider.mycluster</name>
      <value>org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider</value>
    </property>
    <property>
      <name>dfs.ha.fencing.methods</name>
      <value>sshfence</value>
    </property>
    <property>
      <name>dfs.ha.fencing.ssh.private-key-files</name>
      <value>/home/hadoop/.ssh/id_rsa</value>
    </property>
    <property>
      <name>dfs.ha.fencing.ssh.connect-timeout</name>
      <value>30000</value>
    </property>
    <property>
      <name>dfs.journalnode.edits.dir</name>
      <value>/opt/hadoop/journaldata</value>
    </property>
    <property>
      <name>dfs.ha.nn.not-become-active-in-safemode</name>
      <value>true</value>
    </property>
     <property>
       <name>dfs.ha.automatic-failover.enabled</name>
       <value>true</value>
     </property>
</configuration>
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

3./usr/hadoop-3.3.0/etc/hadoop/mapred-site.xml

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
<configuration>
    <property>
        <name>mapreduce.framework.name</name>
        <value>yarn</value>
    </property>
    <property>
        <name>mapreduce.application.classpath</name>
        <value>$HADOOP_HOME/share/hadoop/mapreduce/*:$HADOOP_HOME/share/hadoop/mapreduce/lib/*</value>
    </property>
    <property>
        <name>yarn.app.mapreduce.am.env</name>
        <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
    </property>
    <property>
        <name>mapreduce.map.env</name>
        <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
    </property>
    <property>
        <name>mapreduce.reduce.env</name>
        <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
    </property>
</configuration>
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

4./usr/hadoop-3.3.0/etc/hadoop/yarn-site.xml

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
<configuration>
    <property>
        <name>yarn.nodemanager.aux-services</name>
        <value>mapreduce_shuffle</value>
    </property>
    <property>
        <name>yarn.application.classpath</name>
        <value>这里配置为server01上执行hadoop classpath返回的内容</value>
    </property>
    <property>
        <name>yarn.nodemanager.env-whitelist</name>
        <value>JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_MAPRED_HOME</value>
    </property>
    <property>
      <name>yarn.resourcemanager.ha.enabled</name>
      <value>true</value>
    </property>
    <property>
      <name>yarn.resourcemanager.cluster-id</name>
      <value>cluster1</value>
    </property>
    <property>
      <name>yarn.resourcemanager.ha.rm-ids</name>
      <value>rm1,rm2</value>
    </property>
    <property>
      <name>yarn.resourcemanager.hostname.rm1</name>
      <value>server01</value>
    </property>
    <property>
      <name>yarn.resourcemanager.hostname.rm2</name>
      <value>server02</value>
    </property>
    <property>
      <name>yarn.resourcemanager.webapp.address.rm1</name>
      <value>server01:8088</value>
    </property>
    <property>
      <name>yarn.resourcemanager.webapp.address.rm2</name>
      <value>server02:8088</value>
    </property>
    <property>
      <name>hadoop.zk.address</name>
      <value>server03:2181,server04:2181,server05:2181</value>
    </property>
</configuration>
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

 

5./usr/hadoop-3.3.0/etc/hadoop/works

```
server03
server04
server05
```

###  启动zookeeper

在serverr03、server04、server05三台机器上使用hadoop用户执行zkServer.sh start启动zookeeper。三台都启动之后，在每台机执行zkServer.sh status可以查看本节点是主节点（leader）还是从节点（follower）。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server03 ~]$ zkServer.sh start
/bin/java
ZooKeeper JMX enabled by default
Using config: /usr/apache-zookeeper-3.5.8-bin/bin/../conf/zoo.cfg
Starting zookeeper ... STARTED
[hadoop@server03 ~]$ jps
3383 QuorumPeerMain
3422 Jps
[hadoop@server03 ~]$ zkServer.sh status
/bin/java
ZooKeeper JMX enabled by default
Using config: /usr/apache-zookeeper-3.5.8-bin/bin/../conf/zoo.cfg
Client port found: 2181. Client address: localhost.
Mode: follower
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### 启动JournalNode

在启动journalnode之前，我们在hdfs-site.xml里做了如下配置

```
    <property>
      <name>dfs.journalnode.edits.dir</name>
      <value>/opt/hadoop/journaldata</value>
    </property>
```

我们先用root用户创建这个路径，再把用户和组修改成hadoop

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[root@server04 ~]# cd /opt
[root@server04 opt]# ll
总用量 0
drwxr-xr-x. 3 hadoop hadoop 18 8月   8 23:37 zookeeper
[root@server04 opt]# mkdir -p hadoop/journaldata
[root@server04 opt]# chown -R hadoop:hadoop hadoop/
[root@server04 opt]# ll
总用量 0
drwxr-xr-x. 3 hadoop hadoop 25 8月  11 22:42 hadoop
drwxr-xr-x. 3 hadoop hadoop 18 8月   8 23:37 zookeeper
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

在server03、server04、server05三台机执行hadoop-daemon.sh start journalnode命令，启动journalnode

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server03 sbin]$ hadoop-daemon.sh start journalnode
WARNING: Use of this script to start HDFS daemons is deprecated.
WARNING: Attempting to execute replacement "hdfs --daemon start" instead.
[hadoop@server03 sbin]$ jps
3383 QuorumPeerMain
4439 JournalNode
4476 Jps
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### 格式化HDFS

我们在core-site.xml里有这么一段配置

```
    <property>
      <name>hadoop.tmp.dir</name>
      <value>/opt/hadoop/tmp</value>
    </property>
```

先用root创建这个目录，然后修改用户和组为hadoop

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[root@server01 ~]# cd /opt
[root@server01 opt]# ll
总用量 0
[root@server01 opt]# mkdir -p hadoop/tmp
[root@server01 opt]# chown -R hadoop:hadoop hadoop/
[root@server01 opt]# ll
总用量 0
drwxr-xr-x. 3 hadoop hadoop 17 8月  11 22:54 hadoop
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

在server01上执行hadoop namenode -format，对HDFS格式化。

### 启动Hadoop

第一次启动，在server01上执行hadoop-daemon.sh start namenode。

```
[hadoop@server01 tmp]$ hadoop-daemon.sh start namenode
WARNING: Use of this script to start HDFS daemons is deprecated.
WARNING: Attempting to execute replacement "hdfs --daemon start" instead.
[hadoop@server01 tmp]$ jps
24664 NameNode
24700 Jps
```

在server02上执行hadoop namenode -bootstrapStandby同步server01的信息。

在server01上执行hdfs zkfc -formatZK格式化zkfc。

以上操作完成后，在server01上执行stop-all.sh，把相关的服务全部停掉。

在server01上执行start-all.sh，启动集群。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server01 hadoop]$ start-all.sh
WARNING: Attempting to start all Apache Hadoop daemons as hadoop in 10 seconds.
WARNING: This is not a recommended production deployment configuration.
WARNING: Use CTRL-C to abort.
Starting namenodes on [server01 server02]
Starting datanodes
Starting journal nodes [server04 server03 server05]
Starting ZK Failover Controllers on NN hosts [server01 server02]
Starting resourcemanagers on [ server01 server02]
Starting nodemanagers
[hadoop@server01 hadoop]$ jps
6323 DFSZKFailoverController
5977 NameNode
6668 ResourceManager
6797 Jps
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server02 ~]$ jps
6736 ResourceManager
6600 DFSZKFailoverController
6492 NameNode
7007 Jps
[hadoop@server03 current]$ jps
27705 NodeManager
27978 Jps
5531 QuorumPeerMain
27580 JournalNode
27470 DataNode
[hadoop@server04 tmp]$ jps
5104 QuorumPeerMain
26819 NodeManager
26694 JournalNode
27094 Jps
26584 DataNode
[hadoop@server05 tmp]$ jps
4663 QuorumPeerMain
26394 NodeManager
26268 JournalNode
26684 Jps
26159 DataNode
```

看到这些结果，我忍不住给自己点个赞，让开发人员来搭环境真不容易啊。搭建环境的过程遇到了很多坑，查找资料一一解决，最后成功，这种感觉挺棒的。

## IDEA远程提交MapReduce任务到分布式集群

环境搭好了就该实战一把了。

出一个题目，给出一些日期的气温，从中找出每个月温度最高的两天所对应的气温。注意：一天的气温可能会记录多次，但是结果只能输出最高的一条。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
气温文件
1949-10-01 34c
1949-10-01 38c
1949-10-02 36c
1950-01-01 32c
1950-10-01 37c
1951-12-01 23c
1950-10-02 41c
1950-10-03 27c
1951-07-01 45c
1951-07-02 46c
1951-07-03 47c
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

结题思路：每一行记录可以全部作为Map阶段输出的Key值，按照Key值中的年、月分组，再按照年、月、温度排序，最后去掉一天有多条的记录，输出结果。

### HadoopClient.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.net.URI;

public class HadoopClient {
    public static void main(String[] args) throws Exception {

        //1,conf

        System.setProperty("HADOOP_USER_NAME", "hadoop");
        Configuration conf = new Configuration(true);
        conf.set("mapreduce.app-submission.cross-platform", "true");
        conf.set("mapreduce.job.jar","D:\\IDEAProject\\hadoopmr\\target\\hadoopmr-1.0-SNAPSHOT.jar");

        //2,job
        Job job = Job.getInstance(conf);
        job.setJarByClass(HadoopClient.class);
        job.setJobName("FirstMR");

        //3,输入输出路径
        Path input = new Path("/tq/input/data.txt");
        FileInputFormat.addInputPath(job, input);

        Path output = new Path(URI.create("/tq/output/"));
        //
        if(output.getFileSystem(conf).exists(output)){
            output.getFileSystem(conf).delete(output, true);
        }
        FileOutputFormat.setOutputPath(job, output );

        //4,map
        job.setMapperClass(MyMapper.class);
        job.setMapOutputKeyClass(MyKey.class);
        job.setMapOutputValueClass(IntWritable.class);

        job.setPartitionerClass(MyPartitioner.class);
        job.setSortComparatorClass(MySortComparator.class);


        //5,reduce
        // 分组比较器
        job.setGroupingComparatorClass(MyGroupingComparator.class);
        job.setReducerClass(MyReducer.class);
        job.setNumReduceTasks(1);
        job.setCombinerKeyGroupingComparatorClass(MyGroupingComparator.class);

        //7,submit
        job.waitForCompletion(true);
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MyKey.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MyKey implements WritableComparable<MyKey> {
    private int year;
    private int month;
    private int day;
    private int temperature;

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(year);
        out.writeInt(month);
        out.writeInt(day);
        out.writeInt(temperature);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        this.year=in.readInt();
        this.month=in.readInt();
        this.day=in.readInt();
        this.temperature=in.readInt();
    }

    @Override
    public int compareTo(MyKey that) {
        // 约定俗成：日期正序
        int c1=Integer.compare(this.getYear(), that.getYear());
        if(c1==0) {
            int c2 = Integer.compare(this.getMonth(), that.getMonth());
            if(c2==0) {
                // 比完日期，就没事了
                return Integer.compare(this.getDay(), that.getDay());
            }
            return c2;
        }
        return c1;
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MyMapper.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.util.StringUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MyMapper extends Mapper <LongWritable, Text, MyKey, IntWritable>{
    // 放外面，不用每次都创建！
    MyKey mkey = new MyKey();
    IntWritable mval = new IntWritable();

    @Override
    protected void map(LongWritable key, Text value,Context context) throws IOException, InterruptedException {

        try {
            // value:  1949-10-01 14:21:02   34c  >>  Mykey
            String[] strs = StringUtils.split(value.toString(), ' ');

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date date = sdf.parse(strs[0]);

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            mkey.setYear(cal.get(Calendar.YEAR));
            mkey.setMonth(cal.get(Calendar.MONTH)+1);
            mkey.setDay(cal.get(Calendar.DAY_OF_MONTH));

            int temperature = Integer.parseInt(strs[1].substring(0, strs[1].length()-1));
            mkey.setTemperature(temperature);
            mval.set(temperature);
            context.write(mkey, mval);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MyPartitioner.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Partitioner;

public class MyPartitioner extends  Partitioner<MyKey, IntWritable>{

    @Override
    public int getPartition(MyKey mykey, IntWritable intWritable, int i) {
        return mykey.getYear() % i;
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MySortComparator.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

public class MySortComparator extends WritableComparator {
    public MySortComparator() {
        super(MyKey.class, true);
    }

    @Override
    public int compare(WritableComparable a, WritableComparable b) {
        MyKey t1 = (MyKey)a;
        MyKey t2 = (MyKey)b;

        int c1=Integer.compare(t1.getYear(), t2.getYear());
        if(c1==0){
            int c2=Integer.compare(t1.getMonth(), t2.getMonth());
            if(c2==0){
                // 从大到小，倒序
                return -Integer.compare(t1.getTemperature(), t2.getTemperature());
            }
            return c2;
        }
        return c1;
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MyGroupingComparator.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;

public class MyGroupingComparator extends WritableComparator {
    public MyGroupingComparator() {
        super(MyKey.class,true);
    }

    public int compare(WritableComparable a, WritableComparable b) {
        MyKey t1 = (MyKey)a;
        MyKey t2 = (MyKey)b;

        int c1=Integer.compare(t1.getYear(), t2.getYear());
        if(c1==0){
            return Integer.compare(t1.getMonth(), t2.getMonth());
        }
        return c1;
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

### MyReducer.java

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;


public class MyReducer extends Reducer<MyKey, IntWritable, Text, IntWritable> {
    Text rkey = new Text();
    IntWritable rval = new IntWritable();

    @Override
    protected void reduce(MyKey key, Iterable<IntWritable> values, Context context)
            throws IOException, InterruptedException {
        int flg = 0;
        int day = 0;

        // 1970 01 20  34     34
        // 1970 01 12  28     28
        for (IntWritable v : values) {  // 根本就不用v，key跟着变动的
            if (flg == 0) {
                // 1970-01-20:34
                rkey.set(key.getYear()+"-"+key.getMonth()+"-"+key.getDay());
                rval.set(key.getTemperature());
                context.write(rkey,rval );

                day = key.getDay();
                flg++;
            }
            // 将同一天，多条记录排除
            if(flg!=0 && day != key.getDay()){
                rkey.set(key.getYear()+"-"+key.getMonth()+"-"+key.getDay());
                rval.set(key.getTemperature());
                context.write(rkey,rval);

                break;
            }
        }
    }
}
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

注意，要把core-site.xml、hdfs-stie.xml、mapred-sit.xml、yarn-site.xml这4个文件放到IDEA工程的src/main/resources目录下，resources目录设置为Resources Root。

pom里面增加配置

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
<resources>
      <resource>
        <directory>src/main/resources</directory>
        <includes>
          <include>**/*.properties</include>
          <include>**/*.xml</include>
        </includes>
        <filtering>false</filtering>
      </resource>
      <resource>
        <directory>src/main/java</directory>
        <includes>
          <include>**/*.properties</include>
          <include>**/*.xml</include>
        </includes>
        <filtering>false</filtering>
      </resource>
</resources>
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

以上配置完毕后，执行客户端主程序，用浏览器打开server01:8088查看Applications里的执行结果。执行成功之后，到server01上用命令行查看结果文件。

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

```
[hadoop@server01 ~]$ hdfs dfs -cat /tq/output/part-r-00000
1949-10-1    38
1949-10-2    36
1950-1-1     32
1950-10-2    41
1950-10-1    37
1951-7-3     47
1951-7-2     46
1951-12-1    23
```

[![复制代码](MarkDownImages/%E5%A4%A7%E6%95%B0%E6%8D%AE%E5%AD%A6%E4%B9%A0%EF%BC%8807%EF%BC%89%E2%80%94%E2%80%94Hadoop3.3%E9%AB%98%E5%8F%AF%E7%94%A8%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA.assets/copycode.gif)](javascript:void(0);)

结果无误。回过头来再慢慢看看每一个步骤的代码逻辑是怎么写的，多练习就熟悉了。