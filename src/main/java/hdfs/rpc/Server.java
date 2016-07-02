package hdfs.rpc;

import hdfs.rpc.protocol.proto.MyProtocolProtos.MyProtocol;
import hdfs.rpc.protocolPB.MyProtocolPB;
import hdfs.rpc.protocolPB.MyProtocolServerSideTranslatorPB;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;

import java.io.IOException;

/**
 * Created by Fasten on 2016/7/2.
 *
 * Reference to
 *
 org.apache.hadoop.hdfs.server.namenode.NameNode#main
    // New Namenode instance
    org.apache.hadoop.hdfs.server.namenode.NameNode#createNameNode
        org.apache.hadoop.hdfs.server.namenode.NameNode#NameNode(1)
            org.apache.hadoop.hdfs.server.namenode.NameNode#NameNode(2)
                org.apache.hadoop.hdfs.server.namenode.NameNode#initialize
                    // New FSNamesystem with FSImage
                    org.apache.hadoop.hdfs.server.namenode.NameNode#loadNamesystem
                    // New NameNodeRpcServer instances
                    // serviceRpcServer[listens to requests from DataNodes]
                    // clientRpcServer[listens to requests from clients]
                    // Load protobuf configurations
                    **** org.apache.hadoop.hdfs.server.namenode.NameNode#createRpcServer
                    **** org.apache.hadoop.hdfs.server.namenode.NameNodeRpcServer#NameNodeRpcServer
    // Start NameNodeRpcServer service
    org.apache.hadoop.hdfs.server.namenode.NameNode#join
 *
 */
public class Server {

    public static void main(String[] args) throws IOException {
        Configuration conf = new Configuration();

        RPC.setProtocolEngine(conf, MyProtocolPB.class, ProtobufRpcEngine.class);

        RPC.Server server = new RPC.Builder(conf).setProtocol(MyProtocolPB.class)
                .setInstance(MyProtocol.newReflectiveBlockingService(
                        new MyProtocolServerSideTranslatorPB(new NNRpcServer())))
                .setBindAddress("127.0.0.1")
                .setPort(9000)
                .setNumHandlers(1)
                .setVerbose(true)
                .build();
        server.start();
    }
}
