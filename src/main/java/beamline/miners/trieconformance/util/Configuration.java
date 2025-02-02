package beamline.miners.trieconformance.util;

public class Configuration {


    public enum ConformanceCheckerType
    {
        DISTANCE,
        TRIE_PREFIX,
        TRIE_RANDOM,
        TRIE_RANDOM_STATEFUL,
        TRIE_STREAMING,
        TRIE_EVENT_TIME_AWARE
    }

    public enum LogSortType
    {
        NONE,
        TRACE_LENGTH_ASC,
        TRACE_LENGTH_DESC,
        LEXICOGRAPHIC_ASC,
        LEXICOGRAPHIC_DESC

    }
    public enum MoveType
    {
        SYNCHRONOUS_MOVE,
        LOG_MOVE,
        MODEL_MOVE
    }
    public enum PartialOrderType
    {
        NONE, // partial order is not handled, same as DOLAP version
        NGRAMS,
        GREEDY
    }
    private ConformanceCheckerType confCheckerType;

    private String proxyLog;
    private String inputLog;

    private boolean sort=false;
    private LogSortType sortType;

    private int maxStatesInQueue=100000;
    private int maxTrials=100000;
    private int cleanseFrequency=100;

    private int logMoveCost=1;
    private int modelMoveCost=1;

    // This property is used by Random conformance checkers to determine the frequency of moving from exploitation to exploration
    private int randomPickThreshold;
}
