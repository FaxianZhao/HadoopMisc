package hbase.misc.bulkload;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2;
import org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;

/**
 * Created by Fasten on 2016/6/14.
 */
public class MyBulkLoad extends Configured implements Tool {

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new MyBulkLoad(), args));
    }

    @Override
    public int run(String[] args) throws Exception {
        String input = args[0];
        String tmpOutput = args[1];
        String tablename = args[2];

        TableName tableName = TableName.valueOf(tablename);
        Configuration conf = HBaseConfiguration.create();

        try(Connection conn = ConnectionFactory.createConnection(conf);
            Admin admin = conn.getAdmin();
            Table table = conn.getTable(tableName);
            RegionLocator regionLocator = conn.getRegionLocator(tableName)){

            Job job = Job.getInstance(conf);
            job.setJarByClass(MyBulkLoad.class);
            job.setMapperClass(BulkLoadMapper.class);

            job.setMapOutputKeyClass(ImmutableBytesWritable.class);
            job.setMapOutputValueClass(KeyValue.class);

            FileInputFormat.addInputPath(job, new Path(input));
            FileOutputFormat.setOutputPath(job, new Path(tmpOutput));

            HFileOutputFormat2.configureIncrementalLoad(job, table, regionLocator);

            if(job.waitForCompletion(true)){
                LoadIncrementalHFiles bulkLoader = new LoadIncrementalHFiles(conf);
                bulkLoader.doBulkLoad(new Path(tmpOutput), admin, table, regionLocator);

                // remove tmp files
                /*FileSystem fs = FileSystem.get(conf);
                fs.delete(new Path(tmpOutput), true);*/
                return 0;
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return 1;
    }

    static class BulkLoadMapper extends Mapper<LongWritable, Text, ImmutableBytesWritable, KeyValue>{
        static final byte[] family = Bytes.toBytes("cf");
        static final byte[] column = Bytes.toBytes("col");

        @Override
        protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            String[] kv = line.split(",", 2);
            ImmutableBytesWritable rowKey = new ImmutableBytesWritable(Bytes.toBytes(kv[0]));
            KeyValue keyValue = new KeyValue(Bytes.toBytes(kv[0]),
                    family, column, Bytes.toBytes(kv[1]));
            context.write(rowKey, keyValue);
        }
    }
}
