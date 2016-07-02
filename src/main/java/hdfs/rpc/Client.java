package hdfs.rpc;

import hdfs.rpc.protocol.MyProtocol;
import hdfs.rpc.protocolPB.MyProtocolPB;
import hdfs.rpc.protocolPB.MyProtocolTranslatorPB;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by Fasten on 2016/7/1.
 * Reference to
 *
 org.apache.hadoop.fs.FileSystem#get
    org.apache.hadoop.fs.FileSystem#createFileSystem
        org.apache.hadoop.hdfs.DistributedFileSystem#initialize
        org.apache.hadoop.hdfs.DFSClient#DFSClient(2)
            // Init namenode proxy
            **** org.apache.hadoop.hdfs.NameNodeProxies#createProxy(4)

 org.apache.hadoop.hdfs.DistributedFileSystem#mkdir
     org.apache.hadoop.hdfs.DistributedFileSystem#mkdirsInternal
         org.apache.hadoop.hdfs.DFSClient#mkdirs(4)
             org.apache.hadoop.hdfs.DFSClient#primitiveMkdir(3)
                 // invoke mkdir with namenode proxy
                 org.apache.hadoop.hdfs.protocol.ClientProtocol#mkdirs
                     // invoke mkdir with server side proxy
                     org.apache.hadoop.hdfs.protocolPB.ClientNamenodeProtocolTranslatorPB#mkdirs
 *
 */
public class Client {

    public static void main(String[] args) throws IOException {
        Configuration conf = new Configuration();

        RPC.setProtocolEngine(conf, MyProtocolPB.class, ProtobufRpcEngine.class);

        final long version = RPC.getProtocolVersion(MyProtocolPB.class);

        MyProtocolPB proxy = RPC.getProtocolProxy(MyProtocolPB.class, version,
                new InetSocketAddress("127.0.0.1", 9000), conf).getProxy();

        MyProtocol translatorProxy = new MyProtocolTranslatorPB(proxy);

        // normal operation
        String result1 = translatorProxy.mkdir("path");
        System.out.println(result1);

        // trigger an internal exception
        String result2 = translatorProxy.mkdir("exception");
        System.out.println(result2);
    }
}
