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
叶子索引会写在每个Block的尾部，作为HFile的一部分。Block的尾部同时会包含布隆过滤器和时间范围信息。
布隆过滤器可以帮助我们跳过那些不包含指定行键的文件。时间范围信息可以帮助我们跳过那些不在指定时间范围内的数据。
![HFile结构](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig14.png)

##HFile索引
我们刚才讲到的索引会在HFile被打开的时候加载到内存中。这样我们每次数据查询只需要一次磁盘寻道。
![HFile索引](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig15.png)

##HBase Read Merge
我们已经看到，一行的数据可能同时存在于不同的位置，固化的数据在HFile中，最近修改的数据在MemStore中，最近读过的数据在BlockCache中。
所以，当你读取一行数据时，系统是如何给你返回结果的呢？从BlockCache, MemStore, HFile中产生的一个Read Merge数据:
1. 首先，scanner在BlockCache(读缓存)中寻找数据。数据以LRU的形式存储在这里。
2. 下一步，scanner在MemStore(写缓存)中寻找数据。
3. 如果scanner没有找到所需的全部数据，HBase会使用BlockCache索引和布隆过滤器加载HFile到内存中，来寻找对应数据。
![Read Merge](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig16.png)

##HBase Read Merge
像前面提到的，每个MemStore可能对应多个HFile，这就意味着对于一个读请求，可能会有多个文件被检查，这会影响HBase的效率。
这被称作读取放大(read amplification)。
![Read Amplification](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig17.png)

##HBase Minor Compaction
HBase会自动选取一些较小的HFile并把它们重新写入一些较大的HFile中。这个过程被称为Minor Compaction。
Minor Compaction通过归并排序，将一些较小的HFile重写成 一些较大的HFile。
![Minor Compaction](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig18.png)

##HBase Major Compaction
Major Compaction会把Region中属于同一个ColumnFamily的所有HFile全部重写为一个HFile，并在这个过程中删除所有被标记为删除以及过期的数据。
这对读取效率是一个重大提升，但是Major Compaction会在重写HFile的过程中消耗大量硬盘IO以及网络带宽。这被称为写入放大(Write Amplification)。

Major Compaction可以被指定周期的自动运行。由于读放大的问题，Major Compaction一般被安排在周末或夜间执行。
注意MapR-DB在这方面做了增强，而不需要执行compactions。Major Compaction还会提供一个好处，那些由于服务失败或负载均衡而导致的远程数据服务都会恢复为本地RegionServer服务(原先会读取远端HDFS的数据)。
![Major Compaction](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig19.png)

#Region = 一系列相邻的键
让我们对Region做一个概览:
- 一个表被横向切分为一个或多个Region。一个Region包含从起始键到终止键中间相邻的有序的数据。
- 一个Region默认最大为1GB。
- 一个Region只有一个RegionServer提供对客户端的服务。
- 一个RegionServer可以同时支持1000个Region的服务（无论这些Region是否属于同一个表）
![Region](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig20.png)

##Region Split
对于每个表，初始状态只有一个Region。当一个Region的数据增长到一定程度时，会分裂为两个子Region。每个子Region都为原来Region的一半，而且并行的在RegionServer上提供服务。
分裂信息会报告给HMaster。为了负载均衡，HMaster会把新的Region调度到其他RegionServer上提供服务。
![Region Split](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig21.png)

##Read Load Balancing
Region分裂之后，HMaster会把新的Region调度到其他RegionServer上。结果就是新的Region提供的是远程数据服务，直到HBase做Major Compaction把数据移动到RegionServer本地节点。
HBase进行本地写操作，但当Region移动后（负载均衡或系统恢复），就不是本地服务了，直到下一次Major Compaction。
![负载均衡](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig22.png)

##HDFS Data Replication
所有的读写都是在主节点进行的。HDFS自动复制WAL和HFile的Blocks。HFile block的副本是自动复制的。
HBase的数据安全性靠HDFS提供，因为它所有数据都存储在HDFS上。
当数据写入HDFS中时，一份数据是本地写入，另外两份数据靠HDFS的pipeline机制写入其他两个节点。
![HDFS Data Replication](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig23.png)

##HDFS Data Replication(2)
WAL和HFile都存放在HDFS上，所以HBase如何恢复那些尚未写入HDFS的MemStore数据呢？
下一个部分会回答这个问题。
![HDFS Data Replication 2](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig24.png)

##HBase Crash Recovery
当一个RegionServer崩溃时，这些损坏的Region会处于不可用状态，直到监测和恢复机制被触发。
当Zookeeper无法接收到RegionServer的心跳信息后，它会认为这个RegionServer崩溃了。
HMaster也会收到RegionServer崩溃的信息。

当HMaster检测到RegionServer崩溃后，HMaster会把Region从崩溃的RegionServer上重新分布到其他的存活的RegionServer。
为了恢复崩溃的RegionServer中MemStore上尚未刷写到HDFS中的数据。HMaster会拆分属于崩溃的RegionServer的那些WAL，并把这些文件存储到新的RegionServer的数据节点中。
然后每个RegionServer会分别重放WAL，从而恢复每个Region的MemStore的数据。
![HBase Crash Recovery](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig25.png)

##Data Recovery
WAL文件包含一系列修改，每一个修改都是一个Put或Delete。这些修改都是按时间顺序排列的，所以为了HDFS持久化，新增加的数据都在WAL尾部。

当一个崩溃出现在数据还在内存中，并没有存储到HFile中会怎么样？
WAL会进行重放。读取WAL文件并把这些修改都排序添加到当前的MemStore中。最后MemStore刷写这些数据到HFile。
![Data Recovery](https://www.mapr.com/sites/default/files/blogimages/HBaseArchitecture-Blog-Fig26.png)

##Apache HBase 架构优点
HBase具有以下优点:
- 强一致性模型
    - 当写入完成时，所有client会读取到同样的结果。
- 自动扩展
    - 数据增长到一定水平后，Region会自动分裂
    - 具有HDFS的扩展和冗余性
- 内置恢复
    - 使用WAL(像HDFS的journal log)
- 整合Hadoop
    - 原生支持MapReduce

##Apache HBase 具有的问题
- 业务的连续性和可靠性：
    - WAL重放较慢
    - 对于复杂的故障恢复较慢
    - Major Compaction I/O风暴