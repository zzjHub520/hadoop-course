之前在网上找了很多版本都是 hadoop2 的，为此就写了这一篇文章，并说说在搭建过程中一些配置上与 hadoop2 的小区别

此教程仅是基础教程，所以环境选择为 VMware 下的 CentOS，虚拟机数量为 3，一台 master，两台 slave

首先安装 Linux 中安装 Java，这里不在细说，网上很多教程，唯一要提的就是，如果安装 Oracle JDK，需要先卸载自带的 Open JDK

------

## 虚拟机复制

安装好Java的虚拟机，复制两份，复制完之后，选择“我已复制该虚拟机”，不要选择“我已移动该虚拟机”，否则会发生网络地址冲突

## host 配置

## 查看 ip（所有节点）

```
ifconfig -a
```

或

```
cd /etc/sysconfig/network-scripts/ifcfg-eth0
```

记录下所有的 ip 地址

## 修改 hosts 文件

执行

```
vim /etc/hosts
```

写入相应ip

```text
192.168.190.101 master
192.168.190.102 slave1
192.168.190.103 slave2
```

### 复制配置文件到其他节点

```
scp /etc/hosts slave1:/etc/hosts
scp /etc/hosts slave2:/etc/hosts
```

若采用指定用户（这里为 root）的方式

```
scp /etc/hosts root@slave1:/etc/hosts
scp /etc/hosts root@slave2:/etc/hosts
```

## 修改主机名（所有节点）

### 临时生效，立即生效（所有节点）

```
hostname master
```

### 永久生效（所有节点）

```
vim /etc/sysconfig/network
HOSTNAME=localhost
```

改为

```
HOSTNAME=master
```

其他节点相同（改为 slave1 和 slave2 ）

### 检查主机名

```
hostname
```

## 关闭防火墙（所有节点）

**centos6 的命令：**

立即关闭（所有节点）

```
service iptables stop
```

永久关闭防火墙命令（所有节点）

```
chkconfig iptables off
chkconfig ip6tables off
```

查看防火墙状态命令

```
service iptables status
```

**centos7 的命令：**

关闭防火墙

```
systemctl stop firewalld.service
```

禁止 firewall 开机启动

```
systemctl disable firewalld.service
```

查看防火墙状态

```
firewall-cmd --state
```

### ping 测试（可省略）

```
ping -c 3 slave1
```

3 表示发送 3 个数据包

## 配置 ssh，免密码通信（在 master 上进行）

### 安装

可能没安装，省略

### 生成公钥

执行（每个节点）

```
ssh-keygen -t rsa
```

主要是生成 密钥 和 密钥的存放路径

执行

```
cd ~/.ssh
```

执行

```
ls
```

可以查看 Id_rsa.pub 是共钥文件，id_rsa 是密钥文件

### 将密钥拷贝到其他两个子节点 (两种选其一)

**方法1. 上传公钥**

```
ssh-copy-id -i slave1
ssh-copy-id -i slave2
```

**方法2. scp 复制**

先复制公钥

```
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
```

把 master 上的 authorized_keys 分别复制给 slave1 和 slave2 这两个节点上 `scp authorized_keys slave1:~/.ssh/`

```
scp authorized_keys slave2:~/.ssh/
```

指定用户（这里为 root）的方法

```
scp authorized_keys root@slave1:~/.ssh/
scp authorized_keys root@slave2:~/.ssh/
```

对应主机名请自行修改

### **测试**

### 给 slave1 和 slave2 检查一下是否有主节点传过来的公钥文件

在 slave1 和 slave2 的命令行上分别输入

```
cat authorized_keys
```

### 登陆测试

测试 slave1

在 master 命令行上输入

```
ssh 192.168.190.102
```

退出执行 `exit`

测试 slave2

```
ssh 192.168.190.103
```

退出执行 `exit`

## 安装 Hadoop

## 复制和解压

打开虚拟机共享文件夹

```
cd /mnt/hgfs/xxx
```

xxx 替换为你的虚拟机共享文件夹名

复制

```
cp hadoop-3.2.0.tar.gz /usr/local/hadoop
```

进入 /usr/local 文件夹

```
cd /usr/local
```

解压

```
tar xvzf hadoop-3.2.0.tar.gz
```

改名（我这里没有改）

这里为了方便，我们将 hadoop-3.2.0 文件夹改名为 hadoop

```
mv hadoop-3.2.0 hadoop
```

删除 Hadoop 安装包，以免太占空间

```
rm -rf hadoop-3.2.0.tar.gz
```

## 环境变量（所有节点）

```
vim ~/.bashrc
```

该配置文件仅对当前用户生效

```text
# set hadoop path
export HADOOP_HOME=/usr/local/hadoop
export PATH=$PATH:$HADOOP_HOME/sbin:$HADOOP_HOME/bin
```

执行命令，使配置生效

```
source ~/.bashrc
```

执行命令，进行检查

```
hadoop version
```

注意：这里为了方便，我们仅对 master 上进行了环境变量配置，只有配置了环境变量，系统才能识别 \$HADOOP_HOME 路径，slave 节点是没有配置的，不能识别此路径，所以余下的配置中我们尽量使用绝对路径，而不使用 \$HADOOP_HOME 路径，防止 slave 节点不识别，当然，你也可以使用 scp 命令对 profile 或 .bashrc 文件进行复制，但此操作对于复制的虚拟机操作尚行，实际操作中对不同真实的机器操作就太过为危险了

## 配置 Hadoop

### 创建 HDFS 存储目录

```
cd /usr/local/hadoop
mkdir dfs
cd dfs
mkdir name data tmp
```

/usr/hadoop/dfs/name - 存储namenode文件

/usr/hadoop/dfs/data - 存储数据

/usr/hadoop/dfs/tmp - 存储临时文件

## 配置 Hadoop

创建文件夹

```
cd /usr/local/hadoop
mkdir tmp
mkdir dfs/name
mkdir dfs/data
```

进入配置目录

```
cd $HADOOP_HOME/etc/hadoop/
```

检查路径

```
pwd
```

### 配置 Hadoop 中的 Java 环境变量

```
vim $HADOOP_HOME/etc/hadoop/hadoop-env.sh
vim $HADOOP_HOME/etc/hadoop/yarn-env.sh
```

两个都配置 jdk 路径

```text
# set java environment
export JAVA_HOME=/usr/local/java/jdk1.8
```

### 配置 workers

```
vim $HADOOP_HOME/etc/hadoop/workers
```

删除原内容

添加

slave1

slave2

***注意：这里 hadoop3 与 hadoop2 的配置是有区别的，hadoop2 中的配置文件为 slaves ，而3中已被修改为 workers\***

hadoop2 配置命令

```
vim $HADOOP_HOME/etc/hadoop/slaves
```

### 配置 core-site.xml

```
vim core-site.xml
```

`<configuration></configuration>`中加入

```xml
<configuration>
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://master:9000</value>
    </property>

    <property>
        <name>hadoop.tmp.dir</name>
        <value>/usr/local/hadoop/tmp</value>
    </property>
</configuration>
```

/usr/local/hadoop/hdfs/tmp 已修改为 /usr/local/hadoop/tmp

tmp 文件夹可能需要自行创建

fs.defaultFS 默认端口为 8020

fs.defaultFS 在 2.X 已经替代 fs.default.name

默认配置文件可供参考（不想参考无需执行）

```
vim $HADOOP_HOME/share/doc/hadoop/hadoop-project-dist/hadoop-common/core-default.xml
```

默认配置文件部分配置参数参考

```xml
<property>
    <name>fs.defaultFS</name>
    <value>file:///</value>
    <description>The name of the default file system. A URI whose
        scheme and authority determine the FileSystem implementation. The
        uri's scheme determines the config property (fs.SCHEME.impl) naming
        the FileSystem implementation class. The uri's authority is used to
        determine the host, port, etc. for a filesystem.</description>
</property>


<property>
    <name>hadoop.tmp.dir</name>
    <value>/tmp/hadoop-${user.name}</value>
    <description>A base for other temporary directories.</description>
</property>
```

### 配置 hdfs-site.xml

先在 hadoop-2.6.1 目录下创建 dfs 文件，然后在 dfs 文件里面再创建 name 和 data 这两个文件

```
vim hdfs-site.xml
<property>
    <name>dfs.namenode.secondary.http-address</name>
    <value>master:9000</value>
</property>

<property>
    <name>dfs.namenode.name.dir</name>
    <value>file:/usr/local/hadoop/dfs/name</value>
</property>

<property>
    <name>dfs.datanode.data.dir</name>
    <value>file:/usr/local/hadoop/dfs/data</value>
</property>

<property>
    <name>dfs.replication</name>
    <value>3</value>
</property>
```

dfs.replication 的默认配置即为 3，如果想设置 3 可以不用修改

原配置参数有误，但以防万一，还是保留，hdf 一改为 dfs

默认配置文件可供参考

```
vim $HADOOP_HOME/share/doc/hadoop/hadoop-project-dist/hadoop-hdfs/hdfs-default.xml
```

原默认配置的部分配置参考（Hadoop-version-3）

```xml
<property>
    <name>dfs.namenode.name.dir</name>
    <value>file://${hadoop.tmp.dir}/dfs/name</value>
    <description>Determines where on the local filesystem the DFS name node
        should store the name table(fsimage). If this is a comma-delimited list
        of directories then the name table is replicated in all of the
        directories, for redundancy. 
    </description>
</property>


<property>
    <name>dfs.datanode.data.dir</name>
    <value>file://${hadoop.tmp.dir}/dfs/data</value>
    <description>Determines where on the local filesystem an DFS data node
        should store its blocks. If this is a comma-delimited
        list of directories, then data will be stored in all named
        directories, typically on different devices. The directories should be tagged
        with corresponding storage types ([SSD]/[DISK]/[ARCHIVE]/[RAM_DISK]) for HDFS
        storage policies. The default storage type will be DISK if the directory does
        not have a storage type tagged explicitly. Directories that do not exist will
        be created if local filesystem permission allows.
    </description>
</property>


<property>
    <name>dfs.replication</name>
    <value>3</value>
    <description>Default block replication.
        The actual number of replications can be specified when the file is created.
        The default is used if replication is not specified in create time.
    </description>
</property>


<property>
    <name>dfs.namenode.secondary.http-address</name>
    <value>0.0.0.0:9868</value>
    <description>
        The secondary namenode http server address and port.
    </description>
</property>


<property>
    <name>dfs.namenode.secondary.https-address</name>
    <value>0.0.0.0:9869</value>
    <description>
        The secondary namenode HTTPS server address and port.
    </description>
</property>
```

### 配置 mapred-site.xml

默认没有此文件，得先拷贝模板

```
cp mapred-site.xml.template mapred-site.xml
vim mapred-site.xml
```

`<configuration></configuration>` 中加入

```xml
<property> 
    <name>mapreduce.framework.name</name>
    <value>yarn</value>
</property>
```

### 配置 yarn-site.xml

```
vim yarn-site.xml
<configuration>
    <property>
        <name>yarn.resourcemanager.hostname</name>
        <value>master</value>
    </property>

    <property>
        <name>yarn.nodemanager.aux-services</name>
        <value>mapreduce_shuffle</value> 
    </property>

    <property>
        <name>yarn.nodemanager.vmem-check-enabled</name>
        <value>false</value>
    </property>
</configuration>
```

yarn.resourcemanager.hostname

指定 yarn 的 resourcemanager 的地址

yarn.nodemanager.aux-services

reducer 获取数据的方式

yarn.nodemanager.vmem-check-enabled

忽略虚拟内存的检查

如果安装在虚拟机上，这个配置很有用，配上去之后后续操作不容易出问题。如果是在实体机上，并且内存够多，可以将这个配置去掉

HA 分布式加入的配置

```xml
<!-- HA -->
<property>
    <name>yarn.resourcemanager.ha.enabled</name>
    <value>true</value>
</property>

<property>
    <name>yarn.resourcemanager.cluster-id</name>
    <value>mytest-yarn</value>
</property>             

<property>
    <name>yarn.resourcemanager.ha.rm-ids</name>
    <value>nn1,nn2</value>
</property>

<property>
    <name>yarn.resourcemanager.ha.automatic-failover.enabled</name>
    <value>true</value>
</property>

<property>
    <name>yarn.resourcemanager.hostname.nn1</name>
    <value>namenode-1</value>
</property>

<property>
    <name>yarn.resourcemanager.hostname.nn2</name>
    <value>namenode-2</value>
</property>                     

<property>
    <name>yarn.resourcemanager.webapp.address.nn1</name>
    <value>namenode-1:8088</value>   
</property>         

<property>
    <name>yarn.resourcemanager.webapp.address.nn2</name>
    <value>namenode-2:8088</value> 
</property>

<property>
    <name>yarn.resourcemanager.zk-address</name>
    <value>datanode-1:2181,datanode-2:2181,datanode-3:2181</value>           
</property>
```

### 复制 Hadoop 到其他节点

到 Hadoop 安装的父文件夹

```
scp -rp /usr/local/hadoop slave1:/usr/local/
scp -rp /usr/local/hadoop slave2:/usr/local/
```

指定用户（这里为 root）的方法

```
scp -r /usr/local/hadoop root@slave1:/usr/local
scp -r /usr/local/hadoop root@slave2:/usr/local
```

注意，如果配置中使用了 \$HADOOP_HOME 路径，需要每个机器重新配置一下配置文件，或者直接分发，然后使用 source 命令启动，因为这个文件不在 Hadoop 文件夹里。

默认不建议使用 \$HADOOP_HOME 配置

## 格式化与启动

### 格式化（在 master 上进行即可）

```
hadoop namenode -format
```

注意，如果需要重新格式化 NameNode ，需要先将原来 NameNode 和 DataNode 下的文件全部删除

```
rm -rf $HADOOP_HOME/dfs/data/*
rm -rf $HADOOP_HOME/dfs/name/*
```

每次格式化，都会创建一个新的集群 ID，在目录为 dfs/name/current 和 dfs/data/current 中的 VERSION 文件中，如果不删除原来的目录，会使 namenode 中的 VERSION 文件中是新的集群 ID，而DataNode中是旧的集群 ID，不一致，后续因此会使 datanode 启动失败

但我在查阅资料后发现了网上说有另一种方式解决，就是格式化时指定集群 ID 参数，指定为旧的集群 ID（让输入 Y 或 N 时，输入 N ），注意一定是多次格式化才可以这样操作，即第二次格式化以及往后，此方法并我并没有尝试，有兴趣的可以测试下

格式化成功后，可以看到在 /usr/hadoop/dfs/name 目录下多了一个 current 目录，而且该目录下有一系列文件

### 启动（在 master 上进行即可）

namenode 只能在 master 上启动，因为所有节点的配置都相同，所以 namenode 上启动后，其他节点的进程也会相应启动

执行

```
start-all.sh
```

此命令存在警告，也可以使用其推荐的命令

启动 HDFS 集群

```
start-dfs.sh
```

启动 YARN 集群

```
start-yarn.sh
```

若想关闭请使用如下命令

```
stop-all.sh
```

或

```
stop-yarn.sh
stop-dfs.sh
```

### 查看（所有节点）

执行 `jps`

master 上执行 `jps`，有如下进程

```text
NameNode
SecondaryNameNode
ResourceManager
```

其它节点上执行 `jps` ，有如下进程

```text
DataNode
NodeManager
```



## **访问**



*注意 hadoop 中的端口有改变，以下为官网所列出的 hadoop3 中的端口*

![img](MarkDownImages/hadoop3.2.0%E5%AE%8C%E5%85%A8%E5%88%86%E5%B8%83%E5%BC%8F%E9%9B%86%E7%BE%A4%E6%90%AD%E5%BB%BA.assets/v2-063169dac1b1f760afa14cc91121190b_720w.png)



*官网所列出的 hadoop2 中的端口*

![img](MarkDownImages/hadoop3.2.0%E5%AE%8C%E5%85%A8%E5%88%86%E5%B8%83%E5%BC%8F%E9%9B%86%E7%BE%A4%E6%90%AD%E5%BB%BA.assets/v2-42182a1a0fc82b4da73fb9a94051d84d_720w.png)





**查看HDFS管理页面**

[http://](https://link.zhihu.com/?target=http%3A//192.168.0.104%3A50070/)master:9868



hadoop2 中为50070

也可以通过 ip 访问



## **访问Yarn管理页**

[http://master:8088](https://link.zhihu.com/?target=http%3A//192.168.0.104%3A8088/)

也可以通过 ip 访问



若在 windows 浏览器下访问，通过 ip 太过繁琐，我们也可以设置通过主机名访问

win7 为例，需要将以下信息追加到 C:\Windows\System32\drivers\etc\hosts 文件中

加入

```text
 192.168.190.101 master
 192.168.190.102 slave1
 192.168.190.103 slave2
```



至此 hadoop3.2 的安装已经完成

------

## **FAQ**

## Q1. SSH 登陆时发生异常

> The authenticity of host *** can't be established **** Are you sure you want to continue connecting (yes/no)?

**解决办法：**

方法1. 直接按下 enter 键会出现 Host key verification failed.

输入 yes 再按 enter 就可以了

方法2. 使用 ssh 连接远程主机时加上 “-o StrictHostKeyChecking=no” 的选项，如

```text
ssh -o StrictHostKeyChecking=no 192.168.xxx.xxx
```

方法3. 一个彻底去掉这个提示的方法是，修改 /etc/ssh/ssh_config 文件（或 \$HOME/.ssh/config ）中的配置，添加如下两行配置（不推荐）

```text
StrictHostKeyChecking no
UserKnownHostsFile /dev/null
```

重新启动 sshd 服务即可，执行

```text
/etc/init.d/sshd restart
```

或

```text
service sshd restart
```

注意：使用 windows 登陆 linux 时时 ssh 默认的账户为 admin，而 linux 的管理员账户默认为 root，这里仅需加上账户再连接，如

```text
ssh root@192.168.126.128
```

## Q2. 无法上传文件

> File***could only be replicated to 0 nodes instead of minReplication (=1)

**解决办法：**

检查防火墙

```text
service iptables status
chkconfig iptables off
service iptables stop
```

用 jps 命令查看各 slave 节点的 datanode 有没有启动

删除文件夹 tmp，name，data 文件夹

先停止服务

```text
stop-all.sh
```

删除，所有节点都删

```text
rm -rf /usr/local/hadoop/tmp/*
rm -rf /usr/local/hadoop/dfs/data/*
rm -rf /usr/local/hadoop/dfs/name/*
```

再格式化

```text
hadoop namenode -format
```

启动服务

```text
start-all.sh
```

format 过程中要注意不要频繁地重新格式化 namnode 的 ID 信息。format 过程中选择 N

## Q3. 出现如下警告

> WARN util.NativeCodeLoader: Unable to load native-hadoop library for your platform... using builtin-java classes where applicable

**解决办法：**在文件 [hadoop-env.sh](https://link.zhihu.com/?target=http%3A//hadoop-env.sh) 中 增加

```text
export HADOOP_OPTS="-Djava.library.path=${HADOOP_HOME}/lib/native"
```

## Q4. Hadoop的NameNode处在安全模式下

> Name node is in safe mode

引用网上的一句话：在分布式文件系统启动的时候，开始的时候会有安全模式，当分布式文件系统处于安全模式的情况下，文件系统中的内容不允许修改也不允许删除，直到安全模式结束。安全模式主要是为了系统启动的时候检查各个 DataNode 上数据块的有效性，同时根据策略必要的复制或者删除部分数据块。运行期通过命令也可以进入安全模式。在实践过程中，系统启动的时候去修改和删除文件也会有安全模式不允许修改的出错提示，只需要等待一会儿即可。

**解决办法：**

让 Hadoop 不处在 safe mode 模式下

方法1. 等待一会儿，自动关闭

方法2. 也可以手动关闭安全模式

```text
cd $HADOOP_HOME
bin/hadoop dfsadmin -safemode leave
```



编辑于 2020-01-09 17:21