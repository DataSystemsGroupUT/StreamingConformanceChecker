package beamline.miners.trieconformance;

import beamline.sources.MQTTXesSource;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import beamline.events.BEvent;
import beamline.sources.XesLogSource;

import beamline.miners.trieconformance.TrieConformance.ConformanceResponse;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.BasePathBucketAssigner;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Runner {
    public static void main(String... args) throws Exception {
        //String proxyLog = "input/simple_ooo_log_proxy.xes";
        //String proxyLog = "input/BPI_2012_Sim_2k_random_0.2.xes";
        //String log = "input/simple_ooo_log.xes";
//        String proxyLog = "input/M1_test.xes";
//        String log = "input/M1_test3.xes";
        String proxyLog = "input/BPI_2020_Sim_2k_random_0.5.xes";
//        String log = "input/BPI_2020_1k_sample.xes";

        String timeStamp = ZonedDateTime
                .now(ZoneId.systemDefault())                                // Returns a `ZonedDateTime` object.
                .format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                );

        //XesLogSource source = new XesLogSource(log);

        MQTTXesSource source = new MQTTXesSource("tcp://localhost:1883","test","+");

        TrieConformance conformance = new TrieConformance(proxyLog);

        StreamingFileSink<ConformanceResponse> streamingFileSink = StreamingFileSink.forRowFormat(
                        new Path("output/"+timeStamp), new SimpleStringEncoder<ConformanceResponse>()
                )
                .withBucketAssigner(new BasePathBucketAssigner<>())
                .build();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env
                .addSource(source)
                .keyBy(BEvent::getTraceName)
                .flatMap(conformance)
                .addSink(streamingFileSink).setParallelism(1);
        env.execute();
    }
}
