package beamline.miners.trieconformance;

import beamline.miners.trieconformance.util.Configuration;
import beamline.sources.MQTTXesSource;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.time.Time;
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
//        String logName = "bpi2017";
//        String logName = "bpi2020_travelpermits";
        int minDecayTime = 3;
        float decayTimeMultiplier = 0.3F;
        boolean eventTimeAware = true;
        boolean adaptable = true;
        if (!eventTimeAware){adaptable=false;}
        String settingInfo = "";
        if (!eventTimeAware){settingInfo="_notaware_";}
        if (!adaptable){settingInfo=settingInfo+"_notadaptable_";}

        String proxyLog = "input/"+logName+"_100traces.xes.gz";

        String timeStamp = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                );

        MQTTXesSourceWithEventTime source = new MQTTXesSourceWithEventTime("tcp://localhost:1883","stream","+");

        TrieConformance conformance = new TrieConformance(proxyLog, minDecayTime, decayTimeMultiplier, eventTimeAware, adaptable);


        StreamingFileSink<ConformanceResponse> streamingFileSink = StreamingFileSink.forRowFormat(
                        new Path("output/"+logName+settingInfo+"_"+minDecayTime+"_"+decayTimeMultiplier+"_"+timeStamp), new SimpleStringEncoder<ConformanceResponse>()
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

        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                5,
                Time.milliseconds(5)
        ));

        env.execute();
    }
}
