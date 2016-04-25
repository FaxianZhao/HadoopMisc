package hbase.observer.example;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by Fasten on 2016/4/25.
 */
public class RegionObserverApp {
    private static final Log logger = LogFactory.getLog(RegionObserverApp.class);
    private final int POOL_SIZE = 5;

    private Configuration conf;

    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException, IOException {
        RegionObserverApp app = new RegionObserverApp();
        TableName tableName = TableName.valueOf("testtbl");
        logger.info("init env");
        app.initConf();
        logger.info("setup tbl");
        String coPath = args[0];
        app.setupTbl(coPath, tableName);
        logger.info("fill tbl");
        app.fillTbl(tableName);
        logger.info("test getter");
        app.testGetter(tableName);
        logger.info("test scan");
        app.testScanner(tableName);
    }

    private void initConf() throws IOException {
        conf = HBaseConfiguration.create();
//        conf.set("hbase.zookeeper.quorum", "");
//        conf.set("hbase.cluster.distributed", "true");
    }

    private void setupTbl(String coPath, TableName tableName) throws IOException {

        Path coprocessorPath = new Path(coPath);
        HashMap<String, String> observerParams = new HashMap<>();
        observerParams.put("value", "enhance value");

        try (final Connection conn = ConnectionFactory.createConnection(conf);
             final Admin admin = conn.getAdmin()) {

            HTableDescriptor hTableDescriptor = new HTableDescriptor(tableName);
            HColumnDescriptor columnFamily1 = new HColumnDescriptor("personalDet");
            columnFamily1.setMaxVersions(3);
            hTableDescriptor.addFamily(columnFamily1);
            HColumnDescriptor columnFamily2 = new HColumnDescriptor("salaryDet");
            columnFamily2.setMaxVersions(3);
            hTableDescriptor.addFamily(columnFamily2);

            hTableDescriptor.addCoprocessor(MyRegionObserver.class.getCanonicalName(),
                    coprocessorPath, Coprocessor.PRIORITY_USER, observerParams);

            if (admin.tableExists(tableName)) {
                admin.disableTable(tableName);
                admin.deleteTable(tableName);
            }

            admin.createTable(hTableDescriptor);
            admin.close();
        } catch (IOException e) {
            System.out.println("exception while creating/destroying Connection or Admin\n" + e);
        }
    }

    private void testGetter(TableName tableName) {
        try (final Connection conn = ConnectionFactory.createConnection(conf)) {
            Table table = conn.getTable(tableName);
            Get get = new Get(Bytes.toBytes("admin"));
            Result result = table.get(get);
            System.out.println("Test Getter : " + result.toString());

        } catch (IOException e) {
            System.out.println("exception while create connection\n" + e);
        }

    }

    private void testScanner(TableName tableName) {
        try (final Connection conn = ConnectionFactory.createConnection(conf)) {
            Table table = conn.getTable(tableName);
            ResultScanner results = table.getScanner(new Scan());
            System.out.println("Test Scan : ");
            for (Result result : results) {
                System.out.println(result.toString());
            }

        } catch (IOException e) {
            System.out.println("exception while create connection or get table\n" + e);
        }
    }

    private void fillTbl(TableName tableName) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final BufferedMutator.ExceptionListener listener = new BufferedMutator.ExceptionListener() {

            @Override
            public void onException(RetriesExhaustedWithDetailsException exception, BufferedMutator mutator)
                    throws RetriesExhaustedWithDetailsException {
                for (int i = 0; i < exception.getNumExceptions(); i++) {
                    System.out.println("Failed to sent put " + exception.getRow(i) + " .");
                }
            }
        };

        BufferedMutatorParams params = new BufferedMutatorParams(tableName).listener(listener);
        try (final Connection conn = ConnectionFactory.createConnection(conf);
             final BufferedMutator mutator = conn.getBufferedMutator(params)) {
            logger.info("get mutator");

            final ExecutorService workerPool = Executors.newFixedThreadPool(POOL_SIZE);
            List<Future<Void>> futures = new ArrayList<>();

            futures.add(workerPool.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Put p = new Put(Bytes.toBytes("admin"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("name"),
                            Bytes.toBytes("Admin"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("lastname"),
                            Bytes.toBytes("Admin"));
                    mutator.mutate(p);
                    return null;
                }
            }));

            futures.add(workerPool.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Put p = new Put(Bytes.toBytes("cdickens"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("name"),
                            Bytes.toBytes("Charles"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("lastname"),
                            Bytes.toBytes("Dickens"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("dob"),
                            Bytes.toBytes("02/07/1812"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("gross"),
                            Bytes.toBytes("10000"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("net"),
                            Bytes.toBytes("8000"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("allowances"),
                            Bytes.toBytes("2000"));
                    mutator.mutate(p);
                    return null;
                }
            }));

            futures.add(workerPool.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    Put p = new Put(Bytes.toBytes("jverne"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("name"),
                            Bytes.toBytes("Jules"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("lastname"),
                            Bytes.toBytes("Verne"));
                    p.addColumn(Bytes.toBytes("personalDet"), Bytes.toBytes("dob"),
                            Bytes.toBytes("02/08/1828"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("gross"),
                            Bytes.toBytes("12000"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("net"),
                            Bytes.toBytes("9000"));
                    p.addColumn(Bytes.toBytes("salaryDet"), Bytes.toBytes("allowances"),
                            Bytes.toBytes("3000"));
                    mutator.mutate(p);
                    return null;
                }
            }));

            for (Future<Void> f : futures) {
                f.get(1, TimeUnit.MINUTES);
            }

            workerPool.shutdown();
        } catch (IOException e) {
            System.out.println("exception while creating/destroying Connection or BufferedMutator\n" + e);
        }
    }
}
