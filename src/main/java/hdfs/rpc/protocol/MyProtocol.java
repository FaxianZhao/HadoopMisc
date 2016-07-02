package hdfs.rpc.protocol;

import java.io.IOException;

/**
 * Created by Fasten on 2016/7/1.
 */
public interface MyProtocol {
    public String mkdir(String path) throws IOException;
}
