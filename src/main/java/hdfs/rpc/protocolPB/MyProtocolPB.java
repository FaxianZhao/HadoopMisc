package hdfs.rpc.protocolPB;

import hdfs.rpc.protocol.proto.MyProtocolProtos.MyProtocol;
import org.apache.hadoop.ipc.ProtocolInfo;

/**
 * Created by Fasten on 2016/7/1.
 */
@ProtocolInfo(protocolName = "hdfs.rpc.protocolPB.MyProtocolPB",
        protocolVersion = 1)
public interface MyProtocolPB extends MyProtocol.BlockingInterface{
}
