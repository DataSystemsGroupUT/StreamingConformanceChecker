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
import sources.MQTTXesSourceWithEventTime;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Runner {
    public static void main(String... args) throws Exception {
        String proxyLog = "input/cominds/bpi2017_100traces.xes.gz";
//        String proxyLog = "input/simple_ooo_log_proxy.xes";

        String timeStamp = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                );

        MQTTXesSourceWithEventTime source = new MQTTXesSourceWithEventTime("tcp://localhost:1883","cominds","+");

        TrieConformance conformance = new TrieConformance(proxyLog);

        StreamingFileSink<ConformanceResponse> streamingFileSink = StreamingFileSink.forRowFormat(
                        new Path("output/"+timeStamp), new SimpleStringEncoder<ConformanceResponse>()
                )
                .withBucketAssigner(new BasePathBucketAssigner<>())
                .build();

        System.out.println("Trie built, listening to MQTT stream ...");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env
                .addSource(source)
                .keyBy(BEvent::getTraceName)
                .flatMap(conformance)
                .addSink(streamingFileSink).setParallelism(1);
        env.execute();
    }
}
