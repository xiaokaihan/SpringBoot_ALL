#Redis 分布式集群搭建（CentOS单机版）

Table of Contents
=================

      * [Redis集群的概念](#redis集群的概念)
         * [介绍](#介绍)
         * [数据分片](#数据分片)
         * [主从复制模型](#主从复制模型)
         * [Redis一致性保证](#redis一致性保证)
      * [搭建Redis集群](#搭建redis集群)
      * [搭建环境](#搭建环境)
         * [Centos 7.5 64  腾讯云公共镜像](#centos-75-64--腾讯云公共镜像)
      * [安装 redis 5.0.0](#安装-redis-500)
         * [1、安装编译相关软件包](#1安装编译相关软件包)
         * [2、下载redis并解压](#2下载redis并解压)
         * [3、安装redis](#3安装redis)
         * [4、创建集群目录](#4创建集群目录)
         * [5、复制和修改配置文件](#5复制和修改配置文件)
         * [6、redis集群密码设置](#6redis集群密码设置)
         * [7、配置补充](#7配置补充)
      * [启动Redis](#启动redis)
         * [1、创建Redis启动脚本](#1创建redis启动脚本)
         * [2、执行脚本，查看Redis进程](#2执行脚本查看redis进程)
      * [开启Redis集群](#开启redis集群)
         * [1、创建启动集群的脚本](#1创建启动集群的脚本)
         * [2、运行脚本，启动集群](#2运行脚本启动集群)
      * [Redis集群的使用](#redis集群的使用)
         * [连接集群](#连接集群)
         * [集群中节点挂掉的演示](#集群中节点挂掉的演示)
         * [集群中加入新的节点](#集群中加入新的节点)
            * [增加一个主节点](#增加一个主节点)
            * [增加一个从节点](#增加一个从节点)
         * [移除集群中的一个节点](#移除集群中的一个节点)
            * [移除一个主节点](#移除一个主节点)
            * [移除一个从节点](#移除一个从节点)
      * [Redis性能测试（集群）](#redis性能测试集群)
         * [基准测试](#基准测试)
         * [流水线测试](#流水线测试)
      * [注意问题总结](#注意问题总结)
      * [集群的另一种创建方式](#集群的另一种创建方式)
      * [SpringBoot连接Redis集群](#springboot连接redis集群)


## Redis集群的概念

### 介绍

Redis 集群是一个可以在多个 Redis 节点之间进行数据共享的设施（installation）。

Redis 集群不支持那些需要同时处理多个键的 Redis 命令， 因为执行这些命令需要在多个 Redis 节点之间移动数据， 并且在高负载的情况下， 这些命令将降低 Redis 集群的性能， 并导致不可预测的错误。

Redis 集群通过分区（partition）来提供一定程度的可用性（availability）： 即使集群中有一部分节点失效或者无法进行通讯， 集群也可以继续处理命令请求。

Redis 集群提供了以下两个好处：

- 将数据自动切分（split）到多个节点的能力。
- 当集群中的一部分节点失效或者无法进行通讯时， 仍然可以继续处理命令请求的能力。

### 数据分片

Redis 集群使用数据分片（sharding）而非一致性哈希（consistency hashing）来实现： 一个 Redis 集群包含 16384 个哈希槽（hash slot）， 数据库中的每个键都属于这 16384 个哈希槽的其中一个， 集群使用公式 CRC16(key) % 16384 来计算键 key 属于哪个槽， 其中 CRC16(key) 语句用于计算键 key 的 CRC16 校验和 。

集群中的每个节点负责处理一部分哈希槽。 举个例子， 一个集群可以有三个哈希槽， 其中：

- 节点 A 负责处理 0 号至 5500 号哈希槽。
- 节点 B 负责处理 5501 号至 11000 号哈希槽。
- 节点 C 负责处理 11001 号至 16384 号哈希槽。

这种将哈希槽分布到不同节点的做法使得用户可以很容易地向集群中添加或者删除节点。 比如说：
 我现在想设置一个key,叫`my_name`:

```
set my_name zhangguoji
```

按照Redis Cluster的哈希槽算法，`CRC16('my_name')%16384 = 2412` 那么这个key就被分配到了节点A上
 同样的，当我连接(A,B,C)的任意一个节点想获取`my_name`这个key,都会转到节点A上
 再比如
 如果用户将新节点 D 添加到集群中， 那么集群只需要将节点 A 、B 、 C 中的某些槽移动到节点 D 就可以了。
 增加一个D节点的结果可能如下：

- 节点A覆盖1365-5460
- 节点B覆盖6827-10922
- 节点C覆盖12288-16383
- 节点D覆盖0-1364,5461-6826,10923-1228

与此类似， 如果用户要从集群中移除节点 A ， 那么集群只需要将节点 A 中的所有哈希槽移动到节点 B 和节点 C ， 然后再移除空白（不包含任何哈希槽）的节点 A 就可以了。
 因为将一个哈希槽从一个节点移动到另一个节点不会造成节点阻塞， 所以无论是添加新节点还是移除已存在节点， 又或者改变某个节点包含的哈希槽数量， 都不会造成集群下线。

### 主从复制模型

为了使得集群在一部分节点下线或者无法与集群的大多数（majority）节点进行通讯的情况下， 仍然可以正常运作， Redis 集群对节点使用了主从复制功能： 集群中的每个节点都有 1 个至 N 个复制品（replica）， 其中一个复制品为主节点（master）， 而其余的 N-1 个复制品为从节点（slave）。

在之前列举的节点 A 、B 、C 的例子中， 如果节点 B 下线了， 那么集群将无法正常运行， 因为集群找不到节点来处理 5501 号至 11000号的哈希槽。

另一方面， 假如在创建集群的时候（或者至少在节点 B 下线之前）， 我们为主节点 B 添加了从节点 B1 ， 那么当主节点 B 下线的时候， 集群就会将 B1 设置为新的主节点， 并让它代替下线的主节点 B ， 继续处理 5501 号至 11000 号的哈希槽， 这样集群就不会因为主节点 B 的下线而无法正常运作了。

不过如果节点 B 和 B1 都下线的话， Redis 集群还是会停止运作。

### Redis一致性保证

Redis 并不能保证数据的强一致性. 这意味这在实际中集群在特定的条件下可能会丢失写操作：
 第一个原因是因为集群是用了异步复制. 写操作过程:

1. 客户端向主节点B写入一条命令.
2. 主节点B向客户端回复命令状态.
3. 主节点将写操作复制给他得从节点 B1, B2 和 B3

主节点对命令的复制工作发生在返回命令回复之后， 因为如果每次处理命令请求都需要等待复制操作完成的话， 那么主节点处理命令请求的速度将极大地降低 —— 我们必须在性能和一致性之间做出权衡。 注意：Redis 集群可能会在将来提供同步写的方法。 Redis 集群另外一种可能会丢失命令的情况是集群出现了网络分区， 并且一个客户端与至少包括一个主节点在内的少数实例被孤立。
 举个例子 假设集群包含 A 、 B 、 C 、 A1 、 B1 、 C1 六个节点， 其中 A 、B 、C 为主节点， A1 、B1 、C1 为A，B，C的从节点， 还有一个客户端 Z1 假设集群中发生网络分区，那么集群可能会分为两方，大部分的一方包含节点 A 、C 、A1 、B1 和 C1 ，小部分的一方则包含节点 B 和客户端 Z1 .
 Z1仍然能够向主节点B中写入, 如果网络分区发生时间较短,那么集群将会继续正常运作,如果分区的时间足够让大部分的一方将B1选举为新的master，那么Z1写入B中得数据便丢失了.
 注意， 在网络分裂出现期间， 客户端 Z1 可以向主节点 B 发送写命令的最大时间是有限制的， 这一时间限制称为节点超时时间（node timeout）， 是 Redis 集群的一个重要的配置选项。



## 搭建Redis集群

**要让集群正常工作至少需要3个主节点，在这里我们要创建6个redis节点，其中三个为主节点，三个为从节点，对应的redis节点的ip和端口对应关系如下（为了简单演示都在同一台机器上面）**

```
127.0.0.1:7000

127.0.0.1:7001

127.0.0.1:7002

127.0.0.1:7003

127.0.0.1:7004

127.0.0.1:7005
```



## 搭建环境

### Centos 7.5 64  腾讯云公共镜像

![环境配置](/Users/key/Library/Application Support/typora-user-images/image-20190719100434973.png)



## 安装 redis 5.0.0

### 1、安装编译相关软件包

```shell
yum -y install make gcc gcc-c++ wget
```

### 2、下载redis并解压

```shell
wget http://download.redis.io/releases/redis-5.0.0.tar.gz
tar zxvf redis-5.0.0.tar.gz
```

### 3、安装redis

```shell
cd redis-5.0.0/
make && make PREFIX=/usr/local/redis install
```

将redis安装到了/usr/local/redis目录下

安装如果失败的自行yum安装gcc和tcl（redis安装需要C环境）

```shell
yum install gcc
yum install tcl
```

安装了gcc应该就可以了，根据具体情况而定。

**Noted：安装好Redis之后，把/usr/local/redis/bin 目录下的常用命令拷贝一份用户环境（/usr/bin）下，当我们使用指定命令时，不需要指定路径**

```
cd /usr/local/redis
cp redis-cli /usr/bin/
cp redis-server /usr/bin/
```

以上为常用命令，根据需要而定

### 4、创建集群目录

```shell
cd /usr/local/redis
mkdir cluster
```

### 5、复制和修改配置文件 

**（把解压后的redis-5.0.0文件夹中的配置文件拷贝一份到cluster目录下）**

```shell
cd redis-5.0.0
cp redis.conf /usr/local/redis/cluster/redis-7000.conf
cp redis.conf /usr/local/redis/cluster/redis-7001.conf
cp redis.conf /usr/local/redis/cluster/redis-7002.conf
cp redis.conf /usr/local/redis/cluster/redis-7003.conf
cp redis.conf /usr/local/redis/cluster/redis-7004.conf
cp redis.conf /usr/local/redis/cluster/redis-7005.conf
```

可以直接全部拷贝过去执行。

修改cluster目录下的配置文件（redis-7000.conf～redis-7005.conf）

```
# 端口号
port 7000
# 后台启动
daemonize yes
# 开启集群
cluster-enabled yes
#集群节点配置文件名，不能配置path
cluster-config-file nodes-7000.conf
# 集群连接超时时间
cluster-node-timeout 5000
# 进程pid的文件位置
pidfile /var/run/redis-7000.pid
# 开启aof
appendonly yes
# aof文件名称，不能配置path
appendfilename "appendonly-7000.aof"
# rdb文件名称，不能配置path
dbfilename dump-7000.rdb
```

redis配置文件比较大， 建议使用FTP（如FileZilla）工具将文件下载下来，修改好之后再上传。

6个配置文件按照对应的端口分别修改配置文件， 根据上面的配置依次修改。

配置文件中的各个配置的含义可以参照博客。

[Redis配置详解]: https://www.jianshu.com/p/41f393f594e8

### 6、redis集群密码设置

**1、密码设置(推荐)**
方式一：修改所有Redis集群中的redis.conf文件加入： 

```
masterauth passwd123 
requirepass passwd123 
```

说明：这种方式需要重新启动各节点

方式二：进入各个实例进行设置：

```bash
./redis-cli -c -p 7000 
config set masterauth passwd123 
config set requirepass passwd123 
config rewrite 
```

之后分别使用./redis-cli -c -p 7001，./redis-cli -c -p 7002…..命令给各节点设置上密码。

各个节点密码都必须一致，否则节点之间Redirected时就会失败， 推荐第二种方式，这种方式会把密码写入到redis.conf里面去，且不用重启。

带密码访问集群

```bash
./redis-cli -c -p 7000 -a passwd123
```

### 7、配置补充

 注释掉以下配置， Redis默认本地访问。**远程访问时，需注释掉该配置。**

```
#bind 127.0.0.1
```

如果不为集群设置密码，则把以下配置改为No。当远程访问时，可以无密码访问。

```
protected-mode no
```

如果要为集群设置密码，则每个节点的密码必须一致。



## 启动Redis

### 1、创建Redis启动脚本 

start_redis_cluster.sh

```shell
cd /usr/local/redis
touch start_redis_cluster.sh
chmod +x start_redis_cluster.sh
vim start_redis_cluster.sh
```

脚本内容如下（根据不同配置分别启动6个redis服务）：

```shell
#!/bin/bash
bin/redis-server cluster/redis-7000.conf
bin/redis-server cluster/redis-7001.conf
bin/redis-server cluster/redis-7002.conf
bin/redis-server cluster/redis-7003.conf
bin/redis-server cluster/redis-7004.conf
bin/redis-server cluster/redis-7005.conf
```

### 2、执行脚本，查看Redis进程

```bash
sh start_redis_cluster.sh
ps -ef|grep redis
```

Redis相关进程状态如下：

```shell
root     31814     1  0 Jul18 ?        00:01:06 bin/redis-server *:7000 [cluster]
root     31819     1  0 Jul18 ?        00:01:06 bin/redis-server *:7001 [cluster]
root     31824     1  0 Jul18 ?        00:01:07 bin/redis-server *:7002 [cluster]
root     31829     1  0 Jul18 ?        00:01:03 bin/redis-server *:7003 [cluster]
root     31834     1  0 Jul18 ?        00:01:05 bin/redis-server *:7004 [cluster]
root     31839     1  0 Jul18 ?        00:01:05 bin/redis-server *:7005 [cluster]
```

显示有6个redis进程已经开启，说明我们的redis启动成功了。

这里我们只是开启了6个redis进程，它们只是独立的状态，还没有组成集群。这里我们使用官方提供的工具redis-cli。



## 开启Redis集群

### 1、创建启动集群的脚本

create_redis_cluster.sh

```shell
cd /usr/local/redis
touch create_redis_cluster.sh
chmod +x create_redis_cluster.sh
vim create_redis_cluster.sh
```

脚本内容如下

```shell
#!/bin/bash
/usr/local/redis/bin/redis-cli --cluster create ip:7000  ip:7001 ip:7002 ip:7003 ip:7004  ip:7005 --cluster-replicas 1
```

```
参数说明
--cluster create： 表示创建redis集群
--cluster-replicas 1： 表示为集群中的每一个主节点指定一个从节点，即一比一的复制。
```

Noted:   IP建议使用内网ip， 而不是127.0.0.1。 如果是云服务器，使用外网IP。不然会导致另外一台机器无法连接

### 2、运行脚本，启动集群

```shell
sh create_redis_cluster.sh
```

*注：中途需要输入yes来确认*

```bash
[root@VM_0_8_centos redis]# ./create_redis_cluster
>>> Performing hash slots allocation on 6 nodes...
Master[0] -> Slots 0 - 5460
Master[1] -> Slots 5461 - 10922
Master[2] -> Slots 10923 - 16383
Adding replica 182.254.177.232:7003 to 182.254.177.232:7000
Adding replica 182.254.177.232:7004 to 182.254.177.232:7001
Adding replica 182.254.177.232:7005 to 182.254.177.232:7002
>>> Trying to optimize slaves allocation for anti-affinity
[WARNING] Some slaves are in the same host as their master
M: c3fe4765ae290af543823d7a8504db51a3485f84 182.254.177.232:7000
   slots:[0-5460] (5461 slots) master
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
S: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   replicates c3fe4765ae290af543823d7a8504db51a3485f84
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
Can I set the above configuration? (type 'yes' to accept): yes
>>> Nodes configuration updated
>>> Assign a different config epoch to each node
>>> Sending CLUSTER MEET messages to join the cluster
Waiting for the cluster to join
....
>>> Performing Cluster Check (using node 182.254.177.232:7000)
M: c3fe4765ae290af543823d7a8504db51a3485f84 182.254.177.232:7000
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots: (0 slots) slave
   replicates c3fe4765ae290af543823d7a8504db51a3485f84
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

**以上日志打印，表示集群已经启动成功。**

通过登陆redis客户端验证集群，查看集群信息

`redis-cli -c -p 7000`

`cluster info`

```bash
[root@VM_0_8_centos redis]# redis-cli -c -p 7000
127.0.0.1:7000> cluster info
cluster_state:ok
cluster_slots_assigned:16384
cluster_slots_ok:16384
cluster_slots_pfail:0
cluster_slots_fail:0
cluster_known_nodes:6
cluster_size:3
cluster_current_epoch:6
cluster_my_epoch:1
cluster_stats_messages_ping_sent:276
cluster_stats_messages_pong_sent:282
cluster_stats_messages_sent:558
cluster_stats_messages_ping_received:277
cluster_stats_messages_pong_received:276
cluster_stats_messages_meet_received:5
cluster_stats_messages_received:558
```

cluster_state:ok:，表示集群状态正常。 现在可以通过Redis客户端来操作Redis集群了。



## Redis集群的使用

### 连接集群

使用reids-cli连接集群，使用时加上`-c`参数，就可以连接到集群。

连接7000端口的节点， 其中 `-c` 表示以集群方式连接redis，`-h` 指定ip地址，`-p` 指定端口号

```
cd /usr/local/redis/bin
./redis-cli -c -h 127.0.0.1 -p 7000
```

```bash
[root@VM_0_8_centos ~]# redis-cli -c -h 127.0.0.1 -p 7000
127.0.0.1:7000> set key value1
-> Redirected to slot [12539] located at 182.254.177.232:7002
OK
182.254.177.232:7002> get key
"value1"
182.254.177.232:7002>
```

根据对Redis集群的介绍，可以知道，Redis集群在分配Key时，会使用`CRC16`算法，这里将Key `key`分配到了7002节点上

```bash
Redirected to slot [12539] located at 182.254.177.232:7002
```

redis cluster 采用的方式很暴力，直接重定向到了7002 节点。

当我们在7000-7002任意一个节点执行 set 指令时， 数据会在7000-7002这3个节点之间来回跳转。

```shell
182.254.177.232:7001> set love you
-> Redirected to slot [3168] located at 182.254.177.232:7000
OK
182.254.177.232:7000> set message helloworld
-> Redirected to slot [11537] located at 182.254.177.232:7002
OK
182.254.177.232:7002>
```

> **这就是Redis分布式集群将数据自动切分（split）到多个节点的能力**

### 集群中节点挂掉的演示

上面我们建立了一个集群，3个主节点和3个从节点，7000-7002负责存取数据，7003-7005负责把7000-7002的数据同步到自己的节点上来。

```shell
Adding replica 182.254.177.232:7003 to 182.254.177.232:7000
Adding replica 182.254.177.232:7004 to 182.254.177.232:7001
Adding replica 182.254.177.232:7005 to 182.254.177.232:7002
```

这是我们创建Redis集群时的日志，指定7003为7000的从服务器，从服务器负责同步主服务器的数据，用作备用机。

当一台matser服务器宕机的情况（手动kill一个Redis服务）

```shell
[root@VM_0_8_centos ~]# ps -ef|grep redis
root       622 30060  0 15:41 pts/0    00:00:00 grep --color=auto redis
root      6125     1  0 11:30 ?        00:00:17 bin/redis-server *:7000 [cluster]
root      6127     1  0 11:30 ?        00:00:16 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:17 bin/redis-server *:7002 [cluster]
root      6131     1  0 11:30 ?        00:00:17 bin/redis-server *:7003 [cluster]
root      6133     1  0 11:30 ?        00:00:16 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:16 bin/redis-server *:7005 [cluster]
[root@VM_0_8_centos ~]# kill -9 6125
[root@VM_0_8_centos ~]# ps -ef|grep redis
root       670 30060  0 15:41 pts/0    00:00:00 grep --color=auto redis
root      6127     1  0 11:30 ?        00:00:17 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:17 bin/redis-server *:7002 [cluster]
root      6131     1  0 11:30 ?        00:00:17 bin/redis-server *:7003 [cluster]
root      6133     1  0 11:30 ?        00:00:16 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:16 bin/redis-server *:7005 [cluster]
```

可以看到，已经手动 kill 了`7000` Redis服务器。

然后我们查看`check`一下Redis集群的状况。

```shell
[root@VM_0_8_centos bin]# redis-cli --cluster check 127.0.0.1:7001
Could not connect to Redis at 182.254.177.232:7000: Connection refused
127.0.0.1:7001 (af310376...) -> 3 keys | 5462 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 2 keys | 5461 slots | 1 slaves.
182.254.177.232:7003 (76456a5d...) -> 3 keys | 5461 slots | 0 slaves.
[OK] 8 keys in 3 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7001)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 127.0.0.1:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

从以下打印可以看出，`7003` `slave`服务已经升级为`master`服务，替代了宕掉的 `7000`成为主节点了。

之前`7000`服务器所分配的`slots`, 已经被`7003`服务所接管了。

```shell
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
```

现在我们手动把`7000` 服务启动，再看它是否会自动加入到集群，以及所充当到角色。

Note：单独启动服务的命令要与之前用脚本启动的命令保持一致！！否则会导致不能加入到集群中。

```shell
bin/redis-server cluster/redis-7000.conf
```

```shell
[root@VM_0_8_centos bin]# ps -ef|grep redis
root      2540     1  0 15:58 ?        00:00:00 redis-server *:7000 [cluster]
root      6127     1  0 11:30 ?        00:00:18 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:19 bin/redis-server *:7002 [cluster]
root      6131     1  0 11:30 ?        00:00:19 bin/redis-server *:7003 [cluster]
root      6133     1  0 11:30 ?        00:00:18 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:18 bin/redis-server *:7005 [cluster]
```

通过以上命令， `7000` Redis服务已经重新启动。 

此时，我们再来查看`check`一下Redis集群的状况。

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
182.254.177.232:7002 (4354ebb0...) -> 2 keys | 5461 slots | 1 slaves.
182.254.177.232:7003 (76456a5d...) -> 3 keys | 5461 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 3 keys | 5462 slots | 1 slaves.
[OK] 8 keys in 3 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

从日志可以看到，`7000` 加入到了集群中，并成为了`7003`的 `slave`服务器。（说明了，你挂掉了，就会被替代）

```shell
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
```

接着，我们演示一下`7000`和`7003`两个节点都挂掉的情况（即集群中一对主从服务挂掉）

手动关闭`7000`和`7003`两个节点

```shell
[root@VM_0_8_centos redis]# ps -ef|grep redis
root      3481     1  0 16:06 ?        00:00:00 bin/redis-server *:7000 [cluster]
root      4576 30060  0 16:17 pts/0    00:00:00 grep --color=auto redis
root      6127     1  0 11:30 ?        00:00:20 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:20 bin/redis-server *:7002 [cluster]
root      6131     1  0 11:30 ?        00:00:20 bin/redis-server *:7003 [cluster]
root      6133     1  0 11:30 ?        00:00:19 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:19 bin/redis-server *:7005 [cluster]
[root@VM_0_8_centos redis]# kill -9 3481 6131
[root@VM_0_8_centos redis]# ps -ef|grep redis
root      4640 30060  0 16:17 pts/0    00:00:00 grep --color=auto redis
root      6127     1  0 11:30 ?        00:00:20 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:20 bin/redis-server *:7002 [cluster]
root      6133     1  0 11:30 ?        00:00:19 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:19 bin/redis-server *:7005 [cluster]
```

此时发现集群已经不能正常工作了。

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7001
Could not connect to Redis at 182.254.177.232:7003: Connection refused
Could not connect to Redis at 182.254.177.232:7000: Connection refused
127.0.0.1:7001 (af310376...) -> 3 keys | 5462 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 2 keys | 5461 slots | 1 slaves.
[OK] 5 keys in 2 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7001)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 127.0.0.1:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[ERR] Not all 16384 slots are covered by nodes.
```

两个节点，主节点和从节点都挂掉了，原来7003分配（最开始上7000）的slots现在无节点接管，此时需要人工介入重新分配slots。（人工介入的方式，就是重新启动两个节点加入到集群中。）

最简单的方法就是将`7000`和`7003`重启，重启方式上面已经介绍。 重启完成之后，再查看集群状态

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
182.254.177.232:7003 (76456a5d...) -> 3 keys | 5461 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 3 keys | 5462 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 2 keys | 5461 slots | 1 slaves.
[OK] 8 keys in 3 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

此时集群已经恢复正常了。 

PS：可以看到`7000` 依旧还是`slave`服务，再也回不去`master`了。当然，除非重新创建集群。（说明失去的就回不去了，只能重置了。 所以不要轻易挂掉。）

### 集群中加入新的节点

#### 增加一个主节点

首先cluster目录下再新建一个`redis-7006.conf`的配置文件（修改方式与之前的一样），然后启动这个这个redis进程

```bash
cd /usr/local/redis/cluster
touch redis-7006.conf
```

启动`7006`Redis

```shell
/bin/redis-server cluster/redis-7006.conf
```

```shell
[root@VM_0_8_centos redis]# ps -ef|grep redis
root      5560     1  0 16:26 ?        00:00:01 /bin/redis-server *:7000 [cluster]
root      5583     1  0 16:26 ?        00:00:01 /bin/redis-server *:7003 [cluster]
root      6127     1  0 11:30 ?        00:00:22 bin/redis-server *:7001 [cluster]
root      6129     1  0 11:30 ?        00:00:22 bin/redis-server *:7002 [cluster]
root      6133     1  0 11:30 ?        00:00:21 bin/redis-server *:7004 [cluster]
root      6144     1  0 11:30 ?        00:00:21 bin/redis-server *:7005 [cluster]
root      7340     1  0 16:41 ?        00:00:00 /bin/redis-server *:7006 [cluster]
root      7370 30060  0 16:41 pts/0    00:00:00 grep --color=auto redis
```

可以看到`7006`节点已经启动成功了。

然后再使用redis-cli的`add node`指令加入节点

前面的节点表示要加入的节点`7006`，第二个节点表示要加入的集群中的任意一个节点（`7000`~`7005`都可以），用来标识这个集群。 默认加入`Master`节点。

Note：此处使用`redis-cli --cluster` 添加节点时，IP使用服务器外网IP，或公司内网IP（如果是使用公司内网中的其他服务器访问）， 不要使用`127.0.0.1`，使用`127.0.0.1`会导致集群不能被另外的机器访问。

以下IP，在实际搭建过程中，需要调整！！

```shell
redis-cli --cluster add-node 127.0.0.1:7006 127.0.0.1:7000
```

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster add-node 127.0.0.1:7006 127.0.0.1:7000
>>> Adding node 127.0.0.1:7006 to cluster 127.0.0.1:7000
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
>>> Send CLUSTER MEET to node 127.0.0.1:7006 to make it join the cluster.
[OK] New node added correctly.
```

以上，说明`7006` 节点已经加入到集群中了。

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7006 (9c023f4c...) -> 0 keys | 0 slots | 0 slaves.
182.254.177.232:7003 (76456a5d...) -> 3 keys | 5461 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 3 keys | 5462 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 2 keys | 5461 slots | 1 slaves.
[OK] 8 keys in 4 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006
   slots: (0 slots) master
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

通过查看集信息， 可以看到`7006` 节点成为了一个`Master`服务，但没有关联的从服务节点。

```shell
M: 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006
   slots: (0 slots) master
```

此时，`7006` 节点里面`0 slots`，也就是说节点还没有分配哈希槽，即不能进行数据的存取。

Redis 集群不是在新加节点的时候自动做好了迁移工作，而是需要我们手动对集群进行重新分片迁移的。

```shell
redis-cli --cluster reshard 127.0.0.1:7000
```

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster reshard 127.0.0.1:7000
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006
   slots: (0 slots) master
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[0-5460] (5461 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[5461-10922] (5462 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[10923-16383] (5461 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
How many slots do you want to move (from 1 to 16384)?
```

此时提示我们需要迁移多少`slots`到`7006`节点上，加上`7006`节点一共有4个`master`。我们可以算一下：16384/4 = 4096，也就是说，为了平衡分配，我们需要移动4096个槽点到7006上。

```shell
How many slots do you want to move (from 1 to 16384)? 4096
What is the receiving node ID?
```

接收的node ID就是`7006`的id `9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6`, 上一段信息中可以看到

```shell
How many slots do you want to move (from 1 to 16384)? 4096
What is the receiving node ID? 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6
Please enter all the source node IDs.
  Type 'all' to use all the nodes as source nodes for the hash slots.
  Type 'done' once you entered all the source nodes IDs.
Source node #1:
```

接着， redis-cli 会向你询问重新分片的源节点（source node）， 也即是， 要从哪个节点中取出 4096 个哈希槽， 并将这些槽移动到7006节点上面。

如果我们不打算从特定的节点上取出指定数量的哈希槽， 那么可以向 redis-cli 输入 all ， 这样的话， 集群中的所有主节点都会成为源节点， redis-trib 将从各个源节点中各取出一部分哈希槽， 凑够 4096 个， 然后移动到7006节点上：

```shell
Source node #1: all
...
Do you want to proceed with the proposed reshard plan (yes/no)? yes
```

输入yes并回车后，redis-cli就会正式执行重新分片操作，将制定的哈希槽从源节点一个个移动到7006节点上

迁移结束之后，检查一下

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7006 (9c023f4c...) -> 2 keys | 4096 slots | 0 slaves.
182.254.177.232:7003 (76456a5d...) -> 6 keys | 7167 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 0 keys | 2560 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 0 keys | 2561 slots | 1 slaves.
[OK] 8 keys in 4 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006
   slots:[0-2389],[7510-8362],[12970-13822] (4096 slots) master
S: 42aed905727d1684c71508264aaa61f76b1c1a79 182.254.177.232:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 182.254.177.232:7003
   slots:[2390-7509],[10923-12969] (7167 slots) master
   1 additional replica(s)
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 182.254.177.232:7001
   slots:[8363-10922] (2560 slots) master
   1 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 182.254.177.232:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 182.254.177.232:7002
   slots:[13823-16383] (2561 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

可以看到这些原来在其他节点上的哈希槽都迁移到了7006上了。

```shell
M: 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006
   slots:[0-2389],[7510-8362],[12970-13822] (4096 slots) master
```

#### 增加一个从节点

新建一个 `7007` 节点，作为`7006`的从节点

新建一个节点7007，步骤与新建`7006`类似，就先省略了。

建好后，启动起来后，我们看如何把它加入到集群中的从节点中：

```shell
redis-cli --cluster add-node 127.0.0.1:7007 127.0.0.1:7000 --cluster-slave
```

通过上面的方式，Redis集群会自动将节点`7007` 作为节点 `7006` 的从服务`slave`

因为`7006` 作为`master`，还没有从节点，所以优先分配。 

如果现有的`master`都有`slave`，分配方式是是顺序从`7000`节点开始还是随机分配呢？ 疑惑🤔

为了验证以上问题，我决定再增加一个从节点`7008`。 创建方式同上，不赘述了

最后验证

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7006 (9c023f4c...) -> 2 keys | 4096 slots | 2 slaves.
182.254.177.232:7003 (76456a5d...) -> 6 keys | 7167 slots | 1 slaves.
182.254.177.232:7002 (4354ebb0...) -> 0 keys | 2561 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 0 keys | 2560 slots | 1 slaves.
```

我们可以看到`7008`从节点， 居然是分配到了`7006	`主节点上，`7006` 节点有了两个从节点。 为啥？ 需要去看Redis cluster add-node的原理。

不过我这里决定再创建一个从节点`7009`试试看

结果为

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7006 (9c023f4c...) -> 2 keys | 4096 slots | 2 slaves.
182.254.177.232:7003 (76456a5d...) -> 6 keys | 7167 slots | 2 slaves.
182.254.177.232:7002 (4354ebb0...) -> 0 keys | 2561 slots | 1 slaves.
182.254.177.232:7001 (af310376...) -> 0 keys | 2560 slots | 1 slaves.
```

如此来看，添加从节点时，如果没有指定主节点。 那么从节点会按顺序依次分配给主节点。

接下来介绍如何在添加从节点时，指定一个主节点

```shell
redis-cli --cluster add-node 127.0.0.1:7007 127.0.0.1:7000 --cluster-slave --cluster-master-id ef1bcdb677b1c8f8c3d290a9b1ce2e54f8589835
```

若把节点`7007` 加入到集群，并且是以从节点的形式存在，并且指定`master`为节点的id。此处我们要指定的`master`

节点为`7006`, 将以上id替换从你的集群中`7006` 节点的id 即可。

查看某个主节点下的从节点信息

```shell
redis-cli -c -p 7008 cluster nodes |grep  efc3131fbdc6cf929720e0e0f7136cae85657481
```

`7008`: 可以为Redis集群中的任意节点

efc3131fbdc6cf929720e0e0f7136cae85657481 表示主节点的 id

```shell
[root@VM_0_8_centos redis]# redis-cli -c -p 7008 cluster nodes |grep 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6
9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 127.0.0.1:7006@17006 master - 0 1563532762764 10 connected 0-2389 7510-8362 12970-13822
3f9df889b0503724e1a98b39e65a2058b784d37f 182.254.177.232:7007@17007 slave 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 0 1563532762565 10 connected
34d0e9a41b20e4a9192b36ca50b9915c01b4d64a 172.16.0.8:7008@17008 myself,slave 9c023f4caa0ff9c3f8e2d1936c97303ba52a74a6 0 1563532761000 0 connected
```

以上信息表示， 主节点`7006` 有两个从节点`7007`和`7008`。

我们再做一个测试，我把`7006`的进程杀掉，看`7007`和`7008`谁会变成主节点：

```shell
ps -ef|grep redis
kill -9 pid
```

kill掉节点`7006` 方式如上， 将对应的`pid`替换即可。

```shell
[root@VM_0_8_centos ~]# redis-cli --cluster check 127.0.0.1:7000
Could not connect to Redis at 127.0.0.1:7006: Connection refused
127.0.0.1:7008 (34d0e9a4...) -> 2 keys | 4096 slots | 1 slaves.
127.0.0.1:7003 (76456a5d...) -> 6 keys | 7167 slots | 2 slaves.
127.0.0.1:7002 (4354ebb0...) -> 0 keys | 2561 slots | 1 slaves.
127.0.0.1:7001 (af310376...) -> 1 keys | 2560 slots | 1 slaves.
[OK] 9 keys in 4 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a 127.0.0.1:7008
   slots:[0-2389],[7510-8362],[12970-13822] (4096 slots) master
   1 additional replica(s)
S: 42aed905727d1684c71508264aaa61f76b1c1a79 127.0.0.1:7005
   slots: (0 slots) slave
   replicates 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
M: 76456a5d9715a9840baf599de63ead19918848d2 127.0.0.1:7003
   slots:[2390-7509],[10923-12969] (7167 slots) master
   2 additional replica(s)
S: 0a6d66a58d879e8b4061c4a025d457beabcd5a8a 127.0.0.1:7004
   slots: (0 slots) slave
   replicates af3103766bf26bfc5b37e0b7d6b708a04186b413
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 127.0.0.1:7002
   slots:[13823-16383] (2561 slots) master
   1 additional replica(s)
S: 743e909b98716abdbef6be2d45b74cec05bf5571 127.0.0.1:7009
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
S: 3f9df889b0503724e1a98b39e65a2058b784d37f 127.0.0.1:7007
   slots: (0 slots) slave
   replicates 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
M: af3103766bf26bfc5b37e0b7d6b708a04186b413 127.0.0.1:7001
   slots:[8363-10922] (2560 slots) master
   1 additional replica(s)
[OK] All nodes agree about slots configuration.
>>> Check for open slots...
>>> Check slots coverage...
[OK] All 16384 slots covered.
```

可以看到，这里`7008`获得了成为主节点的机会，`70007`就变成了`7008`的从节点。

```shell
M: 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a 127.0.0.1:7008
   slots:[0-2389],[7510-8362],[12970-13822] (4096 slots) master
   1 additional replica(s)
S: 3f9df889b0503724e1a98b39e65a2058b784d37f 127.0.0.1:7007
   slots: (0 slots) slave
   replicates 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
```

如果此时`7006`节点重新启动的话， 也将成为`7008` 节点的从节点。

### 移除集群中的一个节点

#### 移除一个主节点

移除节点的命令是

```
redis-cli --cluster del-node 127.0.0.1:7000 `<node-id>`
```

这里我们移除`7008` 主节点

34d0e9a41b20e4a9192b36ca50b9915c01b4d64a 为`7008` 的`node-id`。 查看方式以上有命令，不再赘述。

```shell
[root@VM_0_8_centos redis]# redis-cli --cluster del-node 127.0.0.1:7000 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
>>> Removing node 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a from cluster 127.0.0.1:7000
[ERR] Node 127.0.0.1:7008 is not empty! Reshard data away and try again.
```

提示我们`7008`节点里面有数据，让我们把`7008`节点里的数据移除出去，也就是说需要重新分片，这个和上面增加节点的方式一样。

```shell
redis-cli --cluster reshard 127.0.0.1:7000
```

这里可以参照之前增加节点时的代码。在`7008`节点上已经有了4096个哈希槽，里我们也移动4096个哈希槽
然后将这些哈希槽移动到`7002`节点上（任意一个主节点都可以）。

```shell
How many slots do you want to move (from 1 to 16384)? 4096
What is the receiving node ID? 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
Please enter all the source node IDs.
  Type 'all' to use all the nodes as source nodes for the hash slots.
  Type 'done' once you entered all the source nodes IDs.
Source node #1: 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
Source node #2: done
```

` receiving node ID` 是接收的节点ID，这里是`7002`节点

`Source node #1` 表示源节点的ID，这里是`7008`节点

`Source node #2` 表示第二源节点，`done` 表示只有`Source node`

`Source node #1` & `Source node #2` ... —>`receiving node ID`。 一个接收节点，可以有多个源节点。

```shell
What is the receiving node ID? 4354ebb00506be3ffa1da7dc83049d8d95ce2f11
Source node #1: 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
Source node #2: done
```

重新分片完成之后，`7008` 节点就没有数据了。

```shell
[root@VM_0_8_centos ~]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7008 (34d0e9a4...) -> 0 keys | 0 slots | 0 slaves.
127.0.0.1:7003 (76456a5d...) -> 4 keys | 5043 slots | 2 slaves.
127.0.0.1:7002 (4354ebb0...) -> 4 keys | 9539 slots | 3 slaves.
127.0.0.1:7001 (af310376...) -> 1 keys | 1802 slots | 1 slaves.
[OK] 9 keys in 4 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
S: c3fe4765ae290af543823d7a8504db51a3485f84 127.0.0.1:7000
   slots: (0 slots) slave
   replicates 76456a5d9715a9840baf599de63ead19918848d2
M: 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a 127.0.0.1:7008
   slots: (0 slots) master
   ...
```

我们可以接着进行删除操作了。

```shell
[root@VM_0_8_centos ~]# redis-cli --cluster del-node 127.0.0.1:7000 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a
>>> Removing node 34d0e9a41b20e4a9192b36ca50b9915c01b4d64a from cluster 127.0.0.1:7000
>>> Sending CLUSTER FORGET messages to the cluster...
>>> SHUTDOWN the node.
```

移除成功！

通过`check`命令查看集群信息， 发现节点`7008`的两个从节点（`7006`和`7007`）都送给了`7002`节点。

```shell
[root@VM_0_8_centos ~]# redis-cli --cluster check 127.0.0.1:7000
127.0.0.1:7003 (76456a5d...) -> 4 keys | 4096 slots | 2 slaves.
127.0.0.1:7002 (4354ebb0...) -> 4 keys | 8192 slots | 3 slaves.
127.0.0.1:7001 (af310376...) -> 1 keys | 4096 slots | 1 slaves.
[OK] 9 keys in 3 masters.
0.00 keys per slot on average.
>>> Performing Cluster Check (using node 127.0.0.1:7000)
M: 4354ebb00506be3ffa1da7dc83049d8d95ce2f11 127.0.0.1:7002
   slots:[0-4513],[7510-9120],[12970-16383] (8192 slots) master
   3 additional replica(s)
```

#### 移除一个从节点

因为从节点没有哈希槽，就不需要考虑数据迁移，直接移除就行。

这里我们移除`7007` 从节点

```shell
[root@VM_0_8_centos ~]# redis-cli --cluster del-node 127.0.0.1:7000 3f9df889b0503724e1a98b39e65a2058b784d37f
>>> Removing node 3f9df889b0503724e1a98b39e65a2058b784d37f from cluster 127.0.0.1:7000
>>> Sending CLUSTER FORGET messages to the cluster...
>>> SHUTDOWN the node.
```

移除成功！



## Redis性能测试（集群）

### 基准测试

Redis自带了性能测试工具`redis-benchmark`，在`/usr/local/redis/bin` 目录下

`-q`强制退出redis，只显示query/sec值，`-h` 指定服务器，`-p` 指定端口，`-c` 指定并发连接数 .  `-n`指定请求数。 可以根据需求配置。

使用`redis-benchmark --help查看`

```shell
-h <hostname>      Server hostname (default 127.0.0.1)
 -p <port>          Server port (default 6379)
 -s <socket>        Server socket (overrides host and port)
 -a <password>      Password for Redis Auth
 -c <clients>       Number of parallel connections (default 50)
 -n <requests>      Total number of requests (default 100000)
```

```shell
cd /usr/local/redis/bin
cp redis-benchmark /usr/bin
redis-benchmark -q -h 127.0.0.1 -p 7000 -c 50 -n 10000
```

```shell
[root@VM_0_8_centos ~]# redis-benchmark -q -h 127.0.0.1 -p 7000 -c 50 -n 10000
PING_INLINE: 64102.56 requests per second
PING_BULK: 66225.17 requests per second
SET: 67114.09 requests per second
GET: 67114.09 requests per second
INCR: 67567.57 requests per second
LPUSH: 67567.57 requests per second
RPUSH: 67114.09 requests per second
LPOP: 67567.57 requests per second
RPOP: 67567.57 requests per second
SADD: 67114.09 requests per second
HSET: 66225.17 requests per second
SPOP: 68027.21 requests per second
LPUSH (needed to benchmark LRANGE): 67567.57 requests per second
LRANGE_100 (first 100 elements): 67114.09 requests per second
LRANGE_300 (first 300 elements): 67114.09 requests per second
LRANGE_500 (first 450 elements): 63694.27 requests per second
LRANGE_600 (first 600 elements): 65789.48 requests per second
MSET (10 keys): 56818.18 requests per second
```

这里可以看出，Redis集群每秒可以处理的请求数平均在6W以上。666！

接下来介绍一下其他的测试方式

1. 100个并发连接，100000个请求，检测host为localhost 端口为6379的redis服务器性能 

   ```shell
   redis-benchmark -h 127.0.0.1 -p 7000 -c 100 -n 100000 
   ```

2. 测试存取大小为100字节的数据包的性能

   ```shell
   redis-benchmark -h 127.0.0.1 -p 7000 -q -d 100
   ```

3. 只测试某些操作的性能

   ```shell
   redis-benchmark -h 127.0.0.1 -p 7000 -t set,lpush -n 100000 -q
   ```

4. 只测试某些数值存取的性能

   ```shell
   redis-benchmark -h 127.0.0.1 -p 7000 -n 100000 -q script load "redis.call('set','foo','bar')
   ```

### 流水线测试

默认情况下，每个客户端都是在一个请求完成之后才发送下一个请求（基准会模拟50个客户端除非使用-c指定特别的数量），这意味着服务器几乎是按顺序读取每个客户端的命令。RTT也加入了其中。
真实世界会更复杂，Redis支持/topics/pipelining，使得可以一次性执行多条命令成为可能。Redis流水线可以提高服务器的TPS。

```shell
redis-benchmark -h 127.0.0.1 -p 7000 -n 1000000 -t set,get -P 16 -q
```

`-t` 仅运行以逗号分隔的测试命令列表

`-P` 这里P是大写，表示通过管道传输请求 ，加入-P选项使用管道技术，一次执行多条命令

```shell
[root@VM_0_8_centos ~]# redis-benchmark -h 127.0.0.1 -p 7000 -n 1000000 -t set,get -P 16 -q
SET: 508130.06 requests per second
GET: 535905.69 requests per second
```

可以看到，每秒处理get/set请求达到了53/50W。66666！



## 注意问题总结

1. **把redis中常见的命令`cp` 到用户环境中`/usr/bin`，用起来更方便；**

2. **如果集群设置密码，则每个节点的密码必须一致，否则会出现无法连接的情况，测试情况可以不设置**

3. **本文中出现需要IP的地方，都替换成外网IP或者是公司内网IP，尽量不要使用`127.0.0.1`的形式，会导致其他机器无法访问集群。**

4.  启动出现以下问题时

   ```bash
   [ERR] Node 127.0.0.1:7001 is not empty. Either the nodealready knows other nodes (check with CLUSTER NODES) or contains some key in database 0
   ```

   解决方法：
   1). 将需要新增的节点下aof、rdb等本地备份文件删除；
   2). 同时将新Node的集群配置文件删除

   再重新启动

## 集群的另一种创建方式

创建集群的方式`create_cluster.sh`

`create_cluster.sh`命令是Redis自带的创建集群命令。它的使用方式比较简单，但是需要自定义命令文件。

自定义的内容包括`redis.conf`文件的目录， 以及端口的起始值。 这块内容，下篇博客详细介绍。



## SpringBoot连接Redis集群

使用现在最好用的`SpringBoot`框架连接Redis集群，做一些简单的测试。

注意： Redis集群的IP一定不要配置为`127.0.0.1`, 并且注释掉redis.conf配置文件中的以下配置

设置防火墙，把集群涉及到的端口全部放开。

```bash
# bind 127.0.0.1
```

新建一个SpringBoot项目的模版，编辑配置文件application.yaml

具体代码参照github地址：

[SpringBoot+Redis集群]: https://github.com/xiaokaihan/SpringBoot_ALL/tree/master/springboot_redis







