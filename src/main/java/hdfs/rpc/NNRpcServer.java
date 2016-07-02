package hdfs.rpc;

import hdfs.rpc.protocol.MyProtocol;

import java.io.IOException;

/**
 * Created by Fasten on 2016/7/1.
 */
public class NNRpcServer implements MyProtocol {

    @Override
    public String mkdir(String path) throws IOException {
        if("exception".equals(path))
            throw new IOException("internal exception");
        return path + " mkdir successful.";
    }
}

