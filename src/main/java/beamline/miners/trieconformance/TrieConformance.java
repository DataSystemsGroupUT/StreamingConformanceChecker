package beamline.miners.trieconformance;

import beamline.events.BEvent;
import beamline.miners.trieconformance.TrieConformance.ConformanceResponse;
import beamline.miners.trieconformance.util.AlphabetService;
import beamline.models.algorithms.StreamMiningAlgorithm;
import beamline.models.responses.Response;
import org.apache.flink.configuration.Configuration;
import org.deckfour.xes.classification.XEventAttributeClassifier;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.in.XesXmlGZIPParser;
import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import beamline.miners.trieconformance.trie.Trie;
import beamline.miners.trieconformance.util.Configuration.PartialOrderType;

import java.io.*;
import java.util.*;

public class TrieConformance extends StreamMiningAlgorithm<ConformanceResponse> {
    private static Trie proxyTrie;
    private static AlphabetService service;
    private static EventTimeAwareStreamingConformanceChecker checker;

    private static XLog loadLog(String inputProxyLogFile)
    {
        XLog inputProxyLog;
        XesXmlParser parser = null;
        if (inputProxyLogFile.substring(inputProxyLogFile.length()-6).equals("xes.gz"))
            parser = new XesXmlGZIPParser();
        else
            parser = new XesXmlParser();

        try {
            InputStream is = new FileInputStream(inputProxyLogFile);
            inputProxyLog = parser.parse(is).get(0);
            return inputProxyLog;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static Trie constructTrie(String proxyLog, int maxPatternSize){
        service = new AlphabetService();
        XLog inputProxyLog = loadLog(proxyLog);
        try {

            //
            XEventClassifier attClassifier = null;
            if (inputProxyLog.getClassifiers().size()>0)
                attClassifier = inputProxyLog.getClassifiers().get(0);
            else
                attClassifier = new XEventAttributeClassifier("concept:name",new String[]{"concept:name"});
            XLogInfo logInfo = XLogInfoFactory.createLogInfo(inputProxyLog,attClassifier);

            Trie t = new Trie(99999, maxPatternSize);
            List<String> templist;
            for (XTrace trace : inputProxyLog) {
                templist = new ArrayList<String>();
                for (XEvent e : trace) {
                    String label = e.getAttributes().get(attClassifier.getDefiningAttributeKeys()[0]).toString();

                    templist.add(Character.toString(service.alphabetize(label)));
                }

                if (templist.size() > 0) {
                    t.addTrace(templist);
                }

            }


            return t;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;

    }

    public TrieConformance(String proxyLog, int minDecayTime, float decayTimeMultiplier, boolean eventTimeAware, boolean adaptable, int maxPatternSize, PartialOrderType backToTheOrder, float ewmaAlpha) {
        this.proxyTrie = constructTrie(proxyLog, maxPatternSize);
        this.checker = new EventTimeAwareStreamingConformanceChecker(this.proxyTrie, 1,1,100000,100000,minDecayTime,decayTimeMultiplier,true, eventTimeAware, adaptable, backToTheOrder,ewmaAlpha);

    }


    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
    }

    @Override
    public ConformanceResponse ingest(BEvent event) {
        String caseID = event.getTraceName();
        String activityName = event.getEventName();
        Long eventTime = event.getEventTime().getTime();

        if (caseID.equals("test")){return null;}

        Long currTime = System.currentTimeMillis();

        checker.check(new ArrayList<>(Arrays.asList(Character.toString(service.alphabetize(activityName)))),caseID,new ArrayList<>(Arrays.asList(eventTime)));

        State currentOptimalState = checker.getCurrentOptimalState(caseID,false);

//        if (caseID.equals("swap_05_193849")){System.out.println("DEBUG");}

        Long getOptStateTime = System.currentTimeMillis();
        while (currentOptimalState==null){
            currentOptimalState = checker.getCurrentOptimalState(caseID,false);
            if (System.currentTimeMillis()-getOptStateTime>1000){
                return new ConformanceResponse(
                        9999,event, "unknown", 9999L);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        Long timeTaken = System.currentTimeMillis()-currTime;

        return new ConformanceResponse(
                currentOptimalState.getCostSoFar(),event, currentOptimalState.getAlignment().toString(),timeTaken);
                //checkResult.get(caseID).getCostSoFar(), event, checkResult.get(caseID).getAlignment().toString());
    }

    public static class ConformanceResponse extends Response {

        private static final long serialVersionUID = -8148713756624004593L;
        private Integer cost;
        private BEvent lastEvent;
        private String message;
        private long timeTaken;

        public ConformanceResponse(Integer cost, BEvent lastEvent, String message, Long timeTaken) {
            this.cost = cost;
            this.lastEvent = lastEvent;
            this.message = message;
            this.timeTaken = timeTaken;
        }

        public String getMessage() {
            return message;
        }

        public BEvent getLastEvent() {
            return lastEvent;
        }

        public Integer getCost() {
            return cost;
        }

        public Long getTimeTaken() {return timeTaken;}

        public String toString() {
            return lastEvent.getTraceName()+","+lastEvent.getEventName()+","+cost+","+timeTaken+","+System.currentTimeMillis();
        }
    }


}

