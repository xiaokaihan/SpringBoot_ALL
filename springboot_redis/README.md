项目说明：

该项目包含两个部分

一部分是对单机Redis的连接与使用，对应的配置文件为`application-single.yaml`

另一部分是对远程集群的访问，对应的配置文件为`application.yaml`

默认的配置是`application.yaml`。如果使用集群就用该配置。

通过RedisClusterTest 类对集群进行测试。

`config`包中包含了单机和集群的配置，根据需求选定。

通过RedisConfigTest 类进行单机测试。

`service`和`util`包中的是使用Redis时的工具类。