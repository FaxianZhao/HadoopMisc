package htrace.example.distributed;

import org.apache.htrace.core.HTraceConfiguration;
import org.apache.htrace.core.SpanId;
import org.apache.htrace.core.TraceScope;
import org.apache.htrace.core.Tracer;

import java.util.HashMap;

/**
 * Created by Fasten on 2016/4/14.
 *
 * ## prepare environment
 * ## 1. go environment 2. leveldb-devel 3. patchelf
 *
 * How to enable htrace-htraced
 * git clone https://github.com/apache/incubator-htrace.git
 * cd incubator-htrace
 * git checkout rel/4.1
 * cd htrace-htraced
 * go/gobuild.sh
 * if your example does not run on localhost, modify web.address to '0.0.0.0:9096' in
 * example/htraced-conf.xml
 *
 * export HTRACED_CONF_DIR=`pwd`/example
 *
 * start nc or socketServer to listen <listening_port>
 * go/build/htraced -Dstartup.notification.address=localhost:<listening_port>
 * you will get an ticket like below:
 * "{"HttpAddr":"127.0.0.1:9096","HrpcAddr":"[::]:9075","ProcessId":16221}"
 *
 * HrpcAddr is <htracedPort> in the below example code
 * And we can find the trace details in HttpAddr via an http browser
 */
public class DistributedHTracer {

    private HTraceConfiguration conf;

    private Tracer initTracer(final String htracedHost, final String htracedPort) {
        conf = HTraceConfiguration.fromMap(
                new HashMap<String, String>() {{
                    put("tracer.id", "%{tname}");
                    put("span.receiver.classes", "org.apache.htrace.impl.HTracedSpanReceiver");
                    // <htraced_host>:<htraced_port>
                    put("htraced.receiver.address", htracedHost+ ":" + htracedPort);
                    put("sampler.classes", "AlwaysSampler");
//                    put("htraced.receiver.packed", "true");
//                    put("htraced.receiver.max.flush.interval.ms","100");
//                    put("htraced.error.log.period.ms","0");
                }}
        );

        return new Tracer.Builder("MyTracer").
                conf(conf).build();
    }

    private void runExample(final Tracer tracer) {
        try (TraceScope L1Scope = tracer.newScope("Level_1_Scope")) {
            final SpanId L1ScopeId = L1Scope.getSpanId();
            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    TraceScope L2Scope = (L1ScopeId.isValid()) ?
                            tracer.newScope("Level_2_Scope", L1ScopeId) :
                            tracer.newNullScope();
                    // send kv annotation to receiver which belong to current span
                    L2Scope.addKVAnnotation("hello", "world");

                    L2Scope.detach();// disable
                    L2Scope.reattach();// enable

                    // send current timestamp to receiver which belong to current span
                    L2Scope.addTimelineAnnotation("start");
                    try {
                        method();
                        L2Scope.addTimelineAnnotation("end");
                    } finally {
                        L2Scope.close();
                    }
                }
            }, "myWorker");
            t1.start();
        }
    }

    private void method() {
        System.out.println("Invoke Method");
    }

    public static void main(String[] args) {
        DistributedHTracer example = new DistributedHTracer();
        Tracer tracer = example.initTracer("172.18.100.135", "9075");
        example.runExample(tracer);
    }
}
