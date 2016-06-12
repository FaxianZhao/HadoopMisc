package hbase.misc.scan;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.client.metrics.ScanMetrics;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.KeyOnlyFilter;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by Fasten on 2016/6/12.
 *
 * Temporary get row count between time range
 * The better way is create an endpoint in server-side.
 *
 * java -Djava.library.path=$HADOOP_HOME/lib/native ScanTimeRange <tableName> <startDay> <endDay>
 */
public class TimeRangeCount {
    public static void main(String[] args) {

        DateFormat df = new SimpleDateFormat("yyyyMMdd");

        Configuration conf = HBaseConfiguration.create();
        try (Connection conn = ConnectionFactory.createConnection(conf);
             Table table = conn.getTable(TableName.valueOf(args[0]))) {
            Scan scan = new Scan();
            scan.setScanMetricsEnabled(true);
            scan.setTimeRange(df.parse(args[1]).getTime(), df.parse(args[2]).getTime());
            Filter filter = new KeyOnlyFilter();
            scan.setFilter(filter);
            ResultScanner resultScanner = table.getScanner(scan);
            int count = 0;
            for (Result result : resultScanner) {
                count++;
            }

            System.out.println(
                    String.format("Row count of table: %s between %s and %s is %d",
                            args[0], args[1], args[2], count));

            resultScanner.close();

            ScanMetrics metrics = scan.getScanMetrics();
            System.out.println("Maps: " + metrics.getMetricsMap());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
