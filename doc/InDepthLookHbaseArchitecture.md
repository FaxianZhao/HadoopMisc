https://www.mapr.com/blog/in-depth-look-hbase-architecture
https://www.mapr.com/blog/hbase-and-mapr-db-designed-distribution-scale-and-speed
#HBase 架构组件
在物理上，Hbase是主-从结构，由三种服务组件构成。
Region Server提供数据读写服务。当数据传输时，客户端直接和Region Servers交互。
Master负责Region分配以及DDL（增删表）操作。
Zookeeper负责维护集群状态。

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

##HBase第一次读/写
Hbase中有一个特殊的“目录表”叫作Meta表(hbase:meta)，这个表会存储所有region在这个集群中的位置。而Zookeeper会存储这个表所在的位置。
当客户端第一次读/写HBase的时候，会进行以下操作：
1. 客户端从Zookeeper上获取到存储Meta表的RegionServer信息。
2. 客户端从Meta表中读取所需要的表的rowkey所对应的Region信息。并且在客户端缓存Meta表的位置。
3. 从Region信息中获取RegionServer的信息，并从RegionServer读取这一行数据。
对于以后的读操作，客户端会在缓存中检索Meta表的位置以及之前读取的rowkey信息。
而不用每次都去请求Zookeeper，除非有目标Region被移动了，那样才会重新获取Meta信息，并更新缓存。
![Meta缓存](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig6.png)

##HBase Meta表
- Meta表是一张保存了所有Region信息的一张Hbase表。
- Meta表存储的数据像一个b树。
- Meta表的结构如下：
    - 键: Region的起始rowkey, Region ID等
    - 值: Region及RegionServer信息等
![Meta表结构](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig7.png)

##RegionServer组件
RegionServer运行在HDFS的datanode上，包含以下几部分组件：
- WAL:  Write Ahead Log是一个hdfs上的文件(SequenceFile)。用于存储那些尚未被持久存储的新数据(暂存在内存中的)；一般用于故障恢复。
- BlockCache: 读缓存。在内存中存储那些经常被读取的数据，当内存占用满后，以LRU的方式剔除数据。
- MemStore: 写缓存。在内存中存储那些尚未写入HDFS的数据。在写入硬盘之前会被排序。每一个Region的每一个ColumnFamily都会对应一个MemStore。
- 数据按照字典顺序以HFile的方式存储在HDFS。
![RegionServer架构](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig8.png)

##HBase 写过程（1）
当客户端发起一个Put请求，RegionServer首先会把这条数据写入WAL。
WAL:
- WAL以追加的形式写入HDFS。
- 当RegionServer故障后，WAL用于恢复那些尚未被写入HDFS的数据。
![WAL](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig9.png)

##HBase 写过程（2）
一旦数据被写入WAL,它就会被放入MemStore。然后这个Put请求就会返回确认信息给客户端。
![Put确认信息返回](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig10.png)

##HBase MemStore
MemStore以和HFile一样的字典顺序的形式存储这些更新的数据。
Region中的每个ColumnFamily都对应一个MemStore。
![MemStore结构](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig11.png)

##HBase Region刷写
当MemStore累计了足够的数据后，整个排序的数据集都会被当作一个新的HFile写入HDFS。
HBase的每个ColumnFamily都会使用多个HFile，这些HFile包含了Cell或者KeyValue实例。
随着时间的推移，MemStore刷写排序后的更新的数据文件到HDFS。

注意，为什么我们经常限制HBase的ColumnFamily数量。
每一个CF都有一个MemStore，当其中一个MemStore满了，所有这个Region的MemStore都会刷写。
它同时会存储Last Written Sequence Number，以至于HBase会知道已经存储了多少数据。

每个HFile包含的数据中最大的sequence number会作为元信息存储在HFile内部，
用以表示存储结束的位置，以及其他HFile该从哪个位置继续。当Region被加载时，sequence number会被读取，最高的这个sequence number会提供给新数据使用。
![Region刷写](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig12.png)

##HBase HFile
数据以排好序的键值形式存储在HFile中。当MemStore累计足够的数据，MemStore会将这些数据刷写到HDFS中。
这是一种顺序写，它避免了硬盘驱动头的随机移动，所以写入速度非常快。
![HFile](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig13.png)

##HBase HFile结构
HFile中包含一个多层索引，这样可以使HBase可以定位需要的数据而不必读取整个文件。
这个多层索引像一个b+树:
- 键值对以从小到大的顺序存储
- 数据都以64KB的Block形式存储，索引会指向每个Block
- 每个Block都会被一个叶子索引所指向
- 每个Block的最后一个行键都会被放入中间索引
- 根索引指向中间索引
