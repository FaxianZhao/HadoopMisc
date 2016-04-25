package hbase.observer.example;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.InternalScanner;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Fasten on 2016/4/25.
 *
 * shield from target kv
 */
public class MyRegionObserver extends BaseRegionObserver {

    private static final byte[] ADMIN = Bytes.toBytes("admin");
    private static final byte[] COLUMN_FAMILY = Bytes.toBytes("details");
    private static final byte[] COLUMN = Bytes.toBytes("Admin_det");
    private static byte[] VALUE = Bytes.toBytes("You can't see Admin details");

    @Override
    public void start(CoprocessorEnvironment e) throws IOException {
        String value = e.getConfiguration().get("value");
        if (value != null) {
            VALUE = value.getBytes();
        }
    }

    @Override
    public void preGetOp(ObserverContext<RegionCoprocessorEnvironment> e, Get get, List<Cell> results) throws IOException {
        if (Bytes.equals(get.getRow(), ADMIN)) {
            results.add(new KeyValue(ADMIN, COLUMN_FAMILY, COLUMN,
                    System.currentTimeMillis(), KeyValue.Type.Put ,VALUE));
            e.bypass();
        }
    }

    // explicitly remove any admin results from the scan
    @Override
    public boolean postScannerNext(ObserverContext<RegionCoprocessorEnvironment> e, InternalScanner s, List<Result> results, int limit, boolean hasMore) throws IOException {
        Iterator<Result> iter = results.iterator();
        while (iter.hasNext()) {
            if (Bytes.equals(iter.next().getRow(), ADMIN)) {
                iter.remove();
                break;
            }
        }
        return hasMore;
    }
}
