package htrace.example.local;

import org.apache.htrace.core.HTraceConfiguration;
import org.apache.htrace.core.SpanId;
import org.apache.htrace.core.TraceScope;
import org.apache.htrace.core.Tracer;

import java.util.HashMap;

/**
 * Created by Fasten on 2016/4/14.
 */
public class LocalHTracer {
    private HTraceConfiguration conf;

    private Tracer initTracer() {
        conf = HTraceConfiguration.fromMap(
                new HashMap<String, String>() {{
                    put("tracer.id", "%{tname}");
                    put("span.receiver.classes", "StandardOutSpanReceiver");
                    put("sampler.classes", "AlwaysSampler");
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
        LocalHTracer example = new LocalHTracer();
        Tracer tracer = example.initTracer();
        example.runExample(tracer);
    }

}