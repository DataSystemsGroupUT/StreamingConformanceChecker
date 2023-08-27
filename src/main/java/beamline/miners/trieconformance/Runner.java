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

        String logName = "bpi2012";
        int minDecayTime = 3;
        float decayTimeMultiplier = 0.3F;

        String proxyLog = "input/cominds/"+logName+"_100traces.xes.gz";

        String timeStamp = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                );

        MQTTXesSourceWithEventTime source = new MQTTXesSourceWithEventTime("tcp://localhost:1883","cominds","+");

        TrieConformance conformance = new TrieConformance(proxyLog, minDecayTime, decayTimeMultiplier);

        StreamingFileSink<ConformanceResponse> streamingFileSink = StreamingFileSink.forRowFormat(
                        new Path("output/"+logName+"_"+minDecayTime+"_"+decayTimeMultiplier+"_"+timeStamp), new SimpleStringEncoder<ConformanceResponse>()
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
