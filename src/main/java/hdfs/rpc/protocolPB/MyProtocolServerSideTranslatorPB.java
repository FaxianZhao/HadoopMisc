package hdfs.rpc.protocolPB;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import hdfs.rpc.protocol.MyProtocol;
import hdfs.rpc.protocol.proto.MyProtocolProtos.MkdirRequestProto;
import hdfs.rpc.protocol.proto.MyProtocolProtos.MkdirResponseProto;

import java.io.IOException;

/**
 * Created by Fasten on 2016/7/1.
 */
public class MyProtocolServerSideTranslatorPB implements MyProtocolPB {

    final private MyProtocol server;

    public MyProtocolServerSideTranslatorPB(MyProtocol server){
        this.server = server;
    }

    @Override
    public MkdirResponseProto mkdir(RpcController controller, MkdirRequestProto request) throws ServiceException {
        try{
            String result = server.mkdir(request.getPath());
            MkdirResponseProto.Builder builder = MkdirResponseProto.newBuilder();
            MkdirResponseProto response = builder.setResult(result).build();
            return response;
        }catch (IOException e){
            throw new ServiceException(e);
        }
    }
}
