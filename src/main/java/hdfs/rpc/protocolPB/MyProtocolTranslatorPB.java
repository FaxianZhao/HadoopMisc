package hdfs.rpc.protocolPB;

import com.google.protobuf.ServiceException;
import hdfs.rpc.protocol.MyProtocol;
import hdfs.rpc.protocol.proto.MyProtocolProtos.MkdirRequestProto;
import org.apache.hadoop.ipc.ProtobufHelper;

import java.io.IOException;

/**
 * Created by Fasten on 2016/7/1.
 */
public class MyProtocolTranslatorPB implements MyProtocol {

    final private MyProtocolPB rpcProxy;

    public MyProtocolTranslatorPB(MyProtocolPB proxy) throws IOException{
        this.rpcProxy = proxy;
    }

    @Override
    public String mkdir(String path) throws IOException {
        MkdirRequestProto request = MkdirRequestProto.newBuilder()
                .setPath(path)
                .build();
        try{
            return rpcProxy.mkdir(null, request).getResult();
        }catch (ServiceException e) {
            throw ProtobufHelper.getRemoteException(e);
        }
    }
}
