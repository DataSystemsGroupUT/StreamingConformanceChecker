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

import beamline.miners.trieconformance.util.Configuration.PartialOrderType;

public class Runner {
    public static void main(String... args) throws Exception {

        String logName = "bpi2012";
//        String logName = "bpi2017";
//        String logName = "bpi2020_travelpermits";
        int minDecayTime = 3;
        float decayTimeMultiplier = 0.3F;
        boolean eventTimeAware = true;
        boolean adaptable = true;
        String settingInfo = "_btto_adapt"; // _btto_iws, _btto_aware, _btto_adapt

        int maxPatternLength = 10; // defines how large patterns to store during trie construction. use 0 to disable. used for handling partial order
        PartialOrderType backToTheOrder = PartialOrderType.MINITRIE; // NONE = disabled (no partial order handling), MINITRIE = greedy approach, FREQUENCY_RANDOM = frequency approach

        if (!eventTimeAware){adaptable=false;}
        if (!eventTimeAware){settingInfo=settingInfo+"_notaware_";}
        if (!adaptable){settingInfo=settingInfo+"_notadaptable_";}
        if (backToTheOrder==PartialOrderType.MINITRIE){
            settingInfo=settingInfo+"_minitrie_";
        } else if (backToTheOrder==PartialOrderType.FREQUENCY_RANDOM){
            settingInfo=settingInfo+"_freqrandom_";
        }


        String proxyLog = "input/"+logName+"_100traces.xes.gz";
//        String proxyLog = "input/bpi2012_partial_5006.xes.gz"; // used for the BTTO specific experiments

        String timeStamp = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                );
        System.out.println(logName+settingInfo+"_"+minDecayTime+"_"+decayTimeMultiplier+"_"+timeStamp);

        MQTTXesSourceWithEventTime source = new MQTTXesSourceWithEventTime("tcp://localhost:1883","stream","+");

        TrieConformance conformance = new TrieConformance(proxyLog, minDecayTime, decayTimeMultiplier, eventTimeAware, adaptable, maxPatternLength, backToTheOrder);


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
                0,
                Time.milliseconds(1)
        ));

        env.execute();
    }
}
