https://www.mapr.com/blog/in-depth-look-hbase-architecture
https://www.mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed
#HBase 架构组件
在物理上，Hbase是主-从结构，由三种服务组件构成。
Region Server提供数据读写服务。当数据传输时，客户端直接和Region Servers交互。
Master负责Region分配以及DDL（增删表）操作。
Zookeeper(HDFS的一部分)负责维护集群状态。

Region Server提供服务的相关数据都存储在Hadoop DataNode上。也就是说所有HBase数据都存放在HDFS上。
为了使Region Server可以开启数据本地化功能，Region Server最好在DataNode节点部署。
HBase默认进行本地写，但当这个region被移动，就不会再进行本地化操作，直到下一次compaction(major compactioin)。

Namenode维护所有文件的所有物理数据块的元信息。
![整体物理架构](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig1.png)

##Region
HBase的表以行键的字典顺序被水平切分成不同的Region。一个Region包含从起始行键到终止行键中所有的数据。
Region在Region Server上提供读写服务。一个Region Server可以对1000个Region提供服务。
![Region在HBase中的位置](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig2.png)

##HBase Master
Region分配以及DDL（增删表）操作的功能都由HBase Master提供。
Master提供以下服务:
- 调度Region Servers
    - 在启动时分配regions，在恢复/负载均衡是重分配regions
    - 监控集群中所有Region Server的状态（通过zookeeper监听通知）
- 管理员操作
    - 增/改/查表信息
![HMaster在HBase中的位置](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig3.png)

##Zookeeper: 调度器
HBase使用Zookeeper的分布式调度功能来维护集群中的服务状态。
因为Zookeeper的一致性，它被用来维护集群的存活状态，并提供服务失败通知。
注意，至少需要3或5台机器来保证Zookeeper的高可用。
![Zookeeper在HBase中的位置](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig4.png)

##这些组件如何在一起工作的
Zookeeper统一调度分布式服务的状态信息。
每个Region Server都会在Zookeeper创建一个临时节点，并通过心跳来维持这个会话。
![协同工作](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig5.png)
HMaster通过监控这些临时节点来发现Region Server是否存活。
HMaster也会像Region Server一样创建临时节点，然后只会选举出一个激活状态的HMaster。激活状态的HMaster同样以心跳来维持这个会话。
其他待命状态的HMaster都会监听这个节点，用以接收HMaster挂掉的消息。

如果某个Region Server或者激活状态的HMaster没有成功发送心跳，这个会话就会过期，对应的临时节点也会被自动删除。
监听这个临时节点的监听器都会收到节点删除事件。激活状态的HMaster通过监听这种事件尝试恢复Region Server。
待命状态的HMaster通过监听这种事件来成为激活状态的HMaster。