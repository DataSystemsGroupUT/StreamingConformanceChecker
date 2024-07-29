package beamline.miners.trieconformance;

import beamline.miners.trieconformance.alignment.Alignment;
import beamline.miners.trieconformance.alignment.Move;
import beamline.miners.trieconformance.trie.Trie;
import beamline.miners.trieconformance.trie.TrieNode;
import beamline.miners.trieconformance.util.Configuration.PartialOrderType;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class EventTimeAwareStreamingConformanceChecker extends ConformanceChecker{



    protected boolean verbose = false;

    protected ConcurrentLinkedQueue<String> casesSeen;

    // Streaming variables

    protected boolean replayWithLogMoves = true;
    protected int minDecayTime; // = 3;

    protected float decayTimeMultiplierFloor;
    protected volatile float decayTimeMultiplier; // = 0.3F;
    protected boolean discountedDecayTime; // = true; // if set to false then uses fixed minDecayTime value
    protected int averageTrieLength;

    protected boolean eventTimeAware;
    protected boolean adaptable;
    protected int stateLimit;
    protected int caseLimit;

    protected ConcurrentHashMap<String, Pair<TreeMap<Long, List<String>>,List<Pair<String,Long>>>> casesTimelines;

    protected float ewmaAlpha = 0.005F;

    protected PartialOrderType backToTheOrderType;




    public EventTimeAwareStreamingConformanceChecker(Trie trie, int logCost, int modelCost, int stateLimit, int caseLimit, int minDecayTime, float decayTimeMultiplier, boolean discountedDecayTime, boolean eventTimeAware, boolean adaptable, PartialOrderType backToTheOrderType)
    {
        super(trie, logCost, modelCost, stateLimit);
        this.stateLimit = stateLimit; //  to-be implemented
        this.caseLimit = caseLimit;
        this.minDecayTime = minDecayTime;
        this.decayTimeMultiplier = decayTimeMultiplier;
        this.decayTimeMultiplierFloor = decayTimeMultiplier;
        this.discountedDecayTime = discountedDecayTime;

        if (this.discountedDecayTime){
            this.averageTrieLength = trie.getAvgTraceLength();
        }

        this.eventTimeAware = eventTimeAware;
        this.adaptable = adaptable;

        this.casesSeen = new ConcurrentLinkedQueue<>();
        this.casesTimelines = new ConcurrentHashMap<>();
        this.backToTheOrderType = backToTheOrderType;
    }

    public void updateDecayTimeMultiplier(boolean outOfOrder) {
        // Exponentially Weighted Moving Average on boolean data
        float value = outOfOrder ? 1.0F : 0.0F;
        this.decayTimeMultiplier = this.ewmaAlpha * value + (1 - this.ewmaAlpha) * this.decayTimeMultiplier;
        if (this.decayTimeMultiplier < this.decayTimeMultiplierFloor) {
            this.decayTimeMultiplier = this.decayTimeMultiplierFloor;
        }
    }

    public State checkForSyncMoves(String event, State currentState){

        TrieNode prev = currentState.getNode();
        TrieNode node;
        Alignment alg = new Alignment(currentState.getAlignment());
        Move syncMove;

        node = prev.getChild(event);
        if(node==null){
            return null;
        } else {
            syncMove = new Move(event, event, 0);
            alg.appendMove(syncMove);
            prev = node;
            int decayTime = findDecayTime(alg.getTraceSize());

            return new State(alg, new ArrayList<>(), prev, currentState.getCostSoFar(), currentState, decayTime);
        }

    }

    public int findDecayTime(int traceSize){
        int decayTime;
        if(discountedDecayTime){
            decayTime = Math.max(Math.round((averageTrieLength-traceSize)*decayTimeMultiplier),minDecayTime);
        } else {
            decayTime = minDecayTime;
        }
        return decayTime;
    }

    public HashMap<TrieNode,Alignment> findOptimalLeafNode(State state, int costLimit){

        int baseCost = state.getCostSoFar();
        int cost;
        int lastIndex;
        int checkpointLevel;
        int currentLevel;
        int additionalCost;
        TrieNode currentNode;
        TrieNode optimalNode = null;

        List<String> originalPostfix = state.getTracePostfix();
        List<String> postfix;
        int stateLevel = state.getNode().getLevel();
        int originalPostfixSize = originalPostfix.size();
        int postfixSize;
        int logMovesDone;
        int maxLevel = stateLevel+originalPostfixSize+costLimit-1;
        List<TrieNode> leaves = modelTrie.getLeavesFromNode(state.getNode(), maxLevel);
        Map<Integer, String> optimalMoves = new HashMap<>();
        Map<Integer, String> moves;


        for(TrieNode n:leaves){
            //
            if (n.getLevel()>maxLevel){
                continue; // the level limit has been updated and this leaf can never improve the current optimal cost
            }
            postfix = new ArrayList<>(originalPostfix);
            currentNode = n;
            cost = baseCost;
            checkpointLevel = stateLevel+originalPostfixSize;
            currentLevel = currentNode.getLevel();
            moves = new HashMap<>();
            logMovesDone = 0;
            while (cost < costLimit) {
                lastIndex = postfix.lastIndexOf(currentNode.getContent());
                if (lastIndex < 0) {
                    if (currentLevel > checkpointLevel) {
                        cost += 1;
                        moves.put(moves.size(), "model");
                    } else {
                        cost += 2; // we now need to make both a log and model move
                        moves.put(moves.size(), "model");
                        moves.put(moves.size(), "log");
                        logMovesDone++;
                    }
                } else {

                    postfixSize = postfix.size();
                    additionalCost = (postfixSize - lastIndex) - 1;
                    if(additionalCost > 0){
                        while (additionalCost > 0) {
                            if (logMovesDone > 0) {
                                logMovesDone--;
                                additionalCost--;
                                if (additionalCost == 0) {
                                    break;
                                }
                            } else {
                                cost++;
                                moves.put(moves.size(), "log");
                                additionalCost--;
                            }
                        }
                    }
                    moves.put(moves.size(), "sync");
                    postfix.subList(lastIndex, postfixSize).clear();
                    checkpointLevel = stateLevel + postfix.size();
                }

                currentNode = currentNode.getParent();
                currentLevel = currentNode.getLevel();
                if (currentLevel == stateLevel & cost < costLimit & postfix.size() == 0) {
                    // handle new cost limit
                    maxLevel = stateLevel + originalPostfixSize + cost - 1;
                    costLimit = state.getCostSoFar() + cost;
                    optimalNode = n;
                    optimalMoves = moves;
                    break;
                }

                if (currentLevel == stateLevel)
                    break; // cost has not improved but we have reached the original state level
            }
        }


        if(optimalNode==null){
            return null;
        }
        // calculate alignment
        Alignment alg = new Alignment(state.getAlignment());

        currentNode = optimalNode;
        Move m;
        List<Move> movesForAlg = new ArrayList<>();
        int optimalMovesOrigSize = optimalMoves.size();
        while(optimalMoves.size()>0){
            String currentMove = optimalMoves.remove(optimalMovesOrigSize-optimalMoves.size());
            if(currentMove=="model"){
                m = new Move(">>",currentNode.getContent(),1);
                currentNode = currentNode.getParent();
            } else if (currentMove=="log"){
                m = new Move(originalPostfix.remove(originalPostfix.size()-1),">>",1);
            } else {
                String event = originalPostfix.remove(originalPostfix.size()-1);
                m = new Move(event, event, 0);
                currentNode = currentNode.getParent();
            }
            movesForAlg.add(0,m);
        }

        for(Move mv:movesForAlg){
            alg.appendMove(mv);
        }

        HashMap<TrieNode, Alignment> result = new HashMap<>();
        result.put(optimalNode, alg);
        return result;

    }

    public void printCurrentCaseAndStateCounts(){
        long caseCount = 0;
        long stateCount = 0;
        for (StatesBuffer statesList : casesInBuffer.values()){
            caseCount++;
            stateCount += statesList.getCurrentStates().size();
        }
        System.out.print("Case count: ");
        System.out.print(caseCount);
        System.out.print(" | State count: ");
        System.out.print(stateCount);
        System.out.print(" | Curr time: ");
        System.out.print(System.currentTimeMillis());
    }


    public State getCurrentOptimalState(String caseId, boolean finalState){ //
        State state;
        StatesBuffer caseStatesInBuffer;
        ConcurrentHashMap<String, State> currentStates;
        List<State> statesList = new ArrayList<>();

        List<State> optimalStates = new ArrayList<>();
        int currentCost;
        int decayTime;
        State newestState = null;
        List<State> newestStates = new ArrayList<>();
        List<State> oldestStates = new ArrayList<>();
        if (casesInBuffer.containsKey(caseId)){
            caseStatesInBuffer = casesInBuffer.get(caseId);
            currentStates = caseStatesInBuffer.getCurrentStates();
            statesList.addAll(currentStates.values());
            for(State s:statesList){
                if (finalState){

                    // if it is end of trace and end of model, then return that state immediately
                    if(
                            ((s.getTracePostfix().size()+s.getNode().getMinPathLengthToEnd())==0 ||
                                    (s.getTracePostfix().size()==0 && s.getNode().isEndOfTrace()))
                                    && s.getCostSoFar()==0
                    ){
                        return s;
                    }

                    // we are interested in the oldest and newest states
                    if(newestState==null
                            || (s.getDecayTime()>newestState.getDecayTime() & s.getTracePostfix().size()<newestState.getTracePostfix().size())
                            || (s.getDecayTime()>newestState.getDecayTime() & s.getTracePostfix().size()==newestState.getTracePostfix().size())
                            || (s.getDecayTime()==newestState.getDecayTime() & s.getTracePostfix().size()<newestState.getTracePostfix().size())
                    ){
                        newestState = s;
                        newestStates.clear();
                        newestStates.add(s);
                    } else if ((s.getDecayTime()==newestState.getDecayTime() & s.getTracePostfix().size()==newestState.getTracePostfix().size())){
                        newestStates.add(s);
                    }

                } else {
                    // just want to return the latest / current state. This state is prefix-alignment type, not full alignment

                    //decayTime = findDecayTime(s.getAlignment().getTraceSize());
                    if(s.getTracePostfix().size()==0){
                        return s;
                    }
                }

            }

            // calculate cost from newestState
            int optimalCost = 9999999;
            Alignment optimalAlg = null;
            TrieNode optimalNode = null;

            for (State s:newestStates){
                currentCost = s.getCostSoFar();

                Alignment alg = s.getAlignment();
                TrieNode currentNode = s.getNode();
                List<String> postfix = new ArrayList<>(s.getTracePostfix());
                // add log moves - should be none
                while(postfix.size()>0){
                    Move m = new Move(postfix.get(0),">>",1);
                    alg.appendMove(m, 1);
                    currentCost++;
                    postfix.remove(0);
                }
                // add model moves

                if (!currentNode.isEndOfTrace()) {
                    while (currentNode.getMinPathLengthToEnd() > 0) {
                        currentNode = currentNode.getChildOnShortestPathToTheEnd();
                        Move m = new Move(">>", currentNode.getContent(), 1);
                        alg.appendMove(m, 1);
                        currentCost++;
                        if (currentNode.isEndOfTrace())
                            break;
                    }
                }

                if (currentCost < optimalCost){
                    optimalCost = currentCost;
                    optimalAlg = alg;
                    optimalNode = currentNode;
                }
            }

            HashMap<TrieNode, Alignment> optimalLeafAlignment = null;
            int oldestStateMinCost = 9999999;
            for (State s: oldestStates){
                HashMap<TrieNode, Alignment> currentLeafAlignment = findOptimalLeafNode(s, optimalCost);
                if(currentLeafAlignment!=null) {
                    Map.Entry<TrieNode, Alignment> entry = currentLeafAlignment.entrySet().iterator().next();
                    if(entry.getValue().getTotalCost()<oldestStateMinCost){
                        optimalLeafAlignment = currentLeafAlignment;
                        oldestStateMinCost = entry.getValue().getTotalCost();
                    }
                }
            }

            if(optimalLeafAlignment!=null){
                Map.Entry<TrieNode,Alignment> entry = optimalLeafAlignment.entrySet().iterator().next();
                return new State(entry.getValue(), new ArrayList<>(), entry.getKey(), entry.getValue().getTotalCost());
            } else {
                return new State(optimalAlg, new ArrayList<>(),optimalNode, optimalCost);
            }


        } else if (finalState){
            // did not find matching ID
            // returning only model moves for shortest path
            TrieNode minNode = modelTrie.getNodeOnShortestTrace();
            TrieNode currentNode = minNode;
            Alignment alg = new Alignment();
            List<Move> moves = new ArrayList<>();
            while (currentNode.getLevel()>0){
                Move m = new Move(">>",currentNode.getContent(),1);
                moves.add(0,m);
                currentNode = currentNode.getParent();
            }
            for(Move m:moves)
                alg.appendMove(m);

            return new State(alg, new ArrayList<>(), minNode, alg.getTotalCost());

        }

        // did not find a matching case ID
        // OR there is no state with most recent decay time and no trace postfix (note: this part should not happen)
        return null;

    }

    public void handleCaseLimit(String caseId){
        if (casesSeen.contains(caseId)) {
            casesSeen.remove(caseId);
            casesSeen.add(caseId);
        } else {
            casesSeen.add(caseId);
        }
        String caseToRemove;
        while (casesSeen.size() > this.caseLimit) {
            try {
                caseToRemove = casesSeen.iterator().next();
                casesInBuffer.remove(caseToRemove);
            } catch (NoSuchElementException e){
                break;
            }
        }
    }

    private static void permute(List<String> input, int start, List<List<String>> permutations) {
        // this is a recursive method for getting all permutations of the list
        if (start == input.size() - 1) {
            // we have reached the first element
            permutations.add(new ArrayList<>(input));
        } else {
            for (int i = start; i < input.size(); i++) {
                Collections.swap(input, start, i);
                permute(input, start + 1, permutations);
                Collections.swap(input, start, i); // this is used for backtracking
            }
        }
    }

    public List<String> backToTheOrder(List <String> input, List<TrieNode> startingNodes){

        List<String> output = new ArrayList<>();;
        List<List<String>> outputCandidates = new ArrayList<>();
        List<String> pattern;

        // if the input gets a full match, we don't compute further
        for (TrieNode n:startingNodes){
            if (modelTrie.matchCompletely(input,n)!=null){
                return input;
            }
        }

        // first we generate all permutations of the input array
        // NB! limit the number of permutations. If input size > 10?
        // Inspiration from: https://www.baeldung.com/java-array-permutations

        List<List<String>> permutations = new ArrayList<>();
        permute(input, 0, permutations);

        // 2nd level: try to get a complete match on any permutation don't compute further
        for (TrieNode n:startingNodes){
            for (List<String> p:permutations){
                if (modelTrie.matchCompletely(p,n)!=null){
                    return p;
                }
            }
        }

        // none of the permutations gets a full match, so we attempt to find the optimal permutation.
        // The PartialOrderType parameter defines which optimizations we attempt.
        if (backToTheOrderType.equals(PartialOrderType.FREQUENCY_RANDOM)) {
            // this is the frequency based approach
            // we choose the permutation that is most frequent in the trie
            List<Map.Entry<List<String>, Integer>> patternFrequencies = modelTrie.getPatternFrequencySorted();
            int currentFrequency = 0;
            int tempFrequency;

            for (Map.Entry<List<String>,Integer> patternMap:patternFrequencies) {
                tempFrequency = patternMap.getValue();
                if ((tempFrequency<currentFrequency)&(outputCandidates.size()>0)){
                    // there is at least one candidate and now the frequency has decreased. Let's break out of the loop
                    break;
                }
                pattern = patternMap.getKey();
                if (permutations.contains(pattern)){
                    outputCandidates.add(pattern);
                }
                currentFrequency = patternMap.getValue();

            }

            // if break-even then get random entry
            if (outputCandidates.size() > 1) {
                Random rand = new Random();
                output = outputCandidates.get(rand.nextInt(outputCandidates.size()));
            } else if (outputCandidates.size() == 1) {
                output = outputCandidates.get(0);
            } else {
                output = input;
            }

        } else if (backToTheOrderType.equals(PartialOrderType.MINITRIE)) {
            // minitrie (greedy) solution
            // we switch between match and matchInHops until one permutation remains or permutations have run out of events
            // OR the hop size becomes larger than permutation size
            int inputSize = input.size();
            boolean solutionFound = false;
            List<List<String>> candidatePermutations = new ArrayList<>(permutations);
            List<List<String>> tempCandidatePermutations;
            TrieNode candidateNode;

            int matchLength;
            int currentMaxLength = 0;
            ConcurrentMap<List<String>,CopyOnWriteArrayList<Pair<List<String>,TrieNode>>> permutationTracker = new ConcurrentHashMap<>();
            List<String> subPermutation;
            CopyOnWriteArrayList<Pair<List<String>,TrieNode>> subPermutationHolder;
            // in this loop, we try to find the permutations that get the longest initial substring match
            for (List<String> p:permutations) {
                for (TrieNode n : startingNodes) {
                    candidateNode = modelTrie.match(p,n);
                    if (candidateNode!=null){
                        matchLength = candidateNode.getLevel()-n.getLevel();
                        if (matchLength>currentMaxLength){
                            currentMaxLength = matchLength;
                            subPermutation = new ArrayList<>(p.subList(currentMaxLength,p.size())); // filter the subperm to only include relevant events
                            permutationTracker.clear(); // clear the list as we have a new optimal candidate
                            subPermutationHolder = new CopyOnWriteArrayList<>();
                            subPermutationHolder.add(new MutablePair<>(subPermutation,candidateNode));
                            permutationTracker.put(p,subPermutationHolder);
                        } else if (matchLength==currentMaxLength){
                            subPermutation = new ArrayList<>(p.subList(currentMaxLength,p.size()));
                            if (permutationTracker.containsKey(p)){
                                permutationTracker.get(p).add(new MutablePair<>(subPermutation,candidateNode));
                            } else {
                                subPermutationHolder = new CopyOnWriteArrayList<>();
                                subPermutationHolder.add(new MutablePair<>(subPermutation,candidateNode));
                                permutationTracker.put(p,subPermutationHolder);
                            }
                        }
                    }
                }
            }

            if (permutationTracker.size()==1){
                // there was only a single match so we return it
                return permutationTracker.keySet().iterator().next();
            }

            // we now have two possibilities:
            // 1) permutationTracker has 0 entries --> there is no match, so we need to do a hop as first step
            // 2) permutationTracker has more than 1 entry --> we need to do hops from the subPermutations
            // NB also need to keep track of hops, as it only makes sense to do hops as long as hops<input.size()
            // e.g. if input = [A,B,C] we should not do more than 2 hops (model moves)

            if (permutationTracker.size()==0) {
                // fill the tracker with all possibilities
                for (List<String> p : permutations) {
                    for (TrieNode n : startingNodes) {
                        subPermutationHolder = new CopyOnWriteArrayList<>();
                        subPermutationHolder.add(new MutablePair<>(p, n));
                        permutationTracker.put(p, subPermutationHolder);
                    }
                }
            }

            ConcurrentMap<List<String>,CopyOnWriteArrayList<Pair<List<String>,TrieNode>>> tempPermutationTracker = new ConcurrentHashMap<>(permutationTracker);
            List<TrieNode> candidateNodes;
            TrieNode startingNode;
            List<String> tempSubPermutation;

            int hops = 2, usedHops = 2;
            int hopLength;
            while (!solutionFound) {
                int currentMinHopLength = Integer.MAX_VALUE;
                permutationTracker.clear();
                for (ConcurrentMap.Entry<List<String>, CopyOnWriteArrayList<Pair<List<String>, TrieNode>>> entry : tempPermutationTracker.entrySet()) {
                    List<String> p = entry.getKey();
                    List<Pair<List<String>, TrieNode>> v = entry.getValue();
                    for (Pair<List<String>, TrieNode> subP : v) {
                        tempSubPermutation = subP.getLeft();
                        startingNode = subP.getRight();
                        candidateNodes = modelTrie.matchInHops(tempSubPermutation.get(0), startingNode, hops);
                        if (candidateNodes != null) {
                            for (TrieNode c : candidateNodes) {
                                hopLength = c.getLevel() - startingNode.getLevel();
                                if (hopLength < currentMinHopLength) {
                                    currentMinHopLength = hopLength;
                                    subPermutation = new ArrayList<>(tempSubPermutation.subList(hops-1, tempSubPermutation.size())); // filter the subperm to only include relevant events
                                    permutationTracker.clear(); // clear the list as we have a new optimal candidate
                                    subPermutationHolder = new CopyOnWriteArrayList<>();
                                    subPermutationHolder.add(new MutablePair<>(subPermutation, c));
                                    permutationTracker.put(p, subPermutationHolder);
                                } else if (hopLength == currentMinHopLength) {
                                    subPermutation = new ArrayList<>(tempSubPermutation.subList(hops-1, tempSubPermutation.size()));
                                    if (permutationTracker.containsKey(p)) {
                                        permutationTracker.get(p).add(new MutablePair<>(subPermutation, c));
                                    } else {
                                        subPermutationHolder = new CopyOnWriteArrayList<>();
                                        subPermutationHolder.add(new MutablePair<>(subPermutation, c));
                                        permutationTracker.put(p, subPermutationHolder);
                                    }
                                }

                            }
                        }

                    }
                }

                usedHops++;

                // two possibilities: permutationTracker empty --> increase hop size (as long as hops < inputSize)
                // permutationTracker not empty --> try to do matches
                //hops++;
                if (permutationTracker.size()==1){
                    // there was only a single match so we return it
                    return permutationTracker.keySet().iterator().next();
                } else if (permutationTracker.size()==0 & usedHops <= inputSize){
                    hops++;
                    // we should retry the hops
                } else if (permutationTracker.size()>0){
                    // try to do match
                    currentMaxLength = 0;
                    tempPermutationTracker = permutationTracker;
                    for (ConcurrentMap.Entry<List<String>, CopyOnWriteArrayList<Pair<List<String>, TrieNode>>> entry : tempPermutationTracker.entrySet()) {
                        List<String> p = entry.getKey();
                        List<Pair<List<String>, TrieNode>> v = entry.getValue();
                        for (Pair<List<String>, TrieNode> subP : v) {
                            tempSubPermutation = subP.getLeft();
                            startingNode = subP.getRight();
                            candidateNode = modelTrie.match(tempSubPermutation,startingNode);
                            if (candidateNode!=null){
                                matchLength = candidateNode.getLevel()-startingNode.getLevel();
                                if (matchLength>currentMaxLength){
                                    currentMaxLength = matchLength;
                                    subPermutation = new ArrayList<>(tempSubPermutation.subList(currentMaxLength,tempSubPermutation.size())); // filter the subperm to only include relevant events
                                    permutationTracker.clear(); // clear the list as we have a new optimal candidate
                                    subPermutationHolder = new CopyOnWriteArrayList<>();
                                    subPermutationHolder.add(new MutablePair<>(subPermutation,candidateNode));
                                    permutationTracker.put(p,subPermutationHolder);
                                } else if (matchLength==currentMaxLength){
                                    subPermutation = new ArrayList<>(tempSubPermutation.subList(currentMaxLength,tempSubPermutation.size()));
                                    if (permutationTracker.containsKey(p)){
                                        permutationTracker.get(p).add(new MutablePair<>(subPermutation,candidateNode));
                                    } else {
                                        subPermutationHolder = new CopyOnWriteArrayList<>();
                                        subPermutationHolder.add(new MutablePair<>(subPermutation,candidateNode));
                                        permutationTracker.put(p,subPermutationHolder);
                                    }
                                }
                            }
                        }
                    }

                    if (permutationTracker.size()==1){
                        // there was only a single match so we return it
                        return permutationTracker.keySet().iterator().next();
                    }

                    tempPermutationTracker = permutationTracker;

                } else if (usedHops>inputSize){
                    solutionFound=true;
                    if (output.size()==0){
                        output = input;
                    }
                }

            }



            // current
        } else {
            // not implemented
            output = input;
        }



        return output;

    }


    public Alignment check(List<String> trace){
        System.out.println("Only implemented for compatibility with interface");
        return new Alignment();
    }

    public ConcurrentHashMap<String, State> check(List<String> trace, String caseId, List<Long> eventTimes)
    {

        traceSize = trace.size();
        Long eventTime = null;
        if (traceSize>1){
            System.out.println("Not implemented!");
            return null;
        } else if(eventTimeAware) {
            eventTime = eventTimes.get(0);
        }
        State state;
        State previousState;
        StatesBuffer caseStatesInBuffer = null;
        Alignment alg;
        List<String> traceSuffix;
        ConcurrentHashMap<String, State> currentStates = new ConcurrentHashMap<>();
        ArrayList<State> syncMoveStates = new ArrayList<>();
        List<String> newTrace = null; //placeholder for out of order event handling

        // Check if case exists in buffer, if yes: populate state buffer; no: create new buffer with root node
        if (getCasesInBuffer().containsKey(caseId))
        {
            // case exists, fetch last state
            caseStatesInBuffer = getCasesInBuffer().get(caseId);
            currentStates = caseStatesInBuffer.getCurrentStates();

            // event time awareness:
            // storage of events received per event time
            // initial solution: assume that activity+event time has to be unique
            // if multiple events on the same time - sort by processed time (need to store this as well!)
            // use a treemap: unix time for key, list of activities for value
            if(eventTimeAware) {
                if (this.casesTimelines.containsKey(caseId)) {
                    Pair<TreeMap<Long, List<String>>, List<Pair<String, Long>>> caseTimelinePairs = this.casesTimelines.get(caseId);
                    TreeMap<Long, List<String>> caseTimeline = caseTimelinePairs.getLeft();
                    List<String> backToTheOrderEvents = null;

                    if ((eventTime <= caseTimeline.lastEntry().getKey()&backToTheOrderType!=PartialOrderType.NONE)|eventTime<caseTimeline.lastEntry().getKey()) {
                        // the new event has an earlier timestamp than seen previously
                        // i.e., this is an out-of-order event

                        // update the decayTime using the EWMA formula if running the method as adaptable
                        // NB this is now in the backToTheOrderType if-else statement, as there is a corner case where the events might actually be in correct order.
                        // (when the new event has the same timestamp as highest timestamp in event time store, and it is in fact a valid order. E.g., we have A,B with timestamp 1 and we get C with timestamp 1 and ABC is valid path in model)



                        // back to the order - check if this timestamp already has events
                        // if yes --> frequency OR minitrie

                        // generate a new trace that needs to be replayed
                        newTrace = new ArrayList<>();
                        NavigableMap<Long, List<String>> higherTimestamps;

                        int numOfEventsToReplay = 0;

                        if (backToTheOrderType==PartialOrderType.NONE){
                            // same behavior as in DOLAP paper (no handling of partial orders)
                            higherTimestamps = caseTimeline.tailMap(eventTime, false);
                            for (List<String> v : higherTimestamps.values()) {
                                numOfEventsToReplay = numOfEventsToReplay + v.size();
                            }
                            newTrace.addAll(trace);
                            if (adaptable) updateDecayTimeMultiplier(true);
                        } else if (caseTimeline.containsKey(eventTime)){
                            // this timestamp has events
                            List<String> partialOrderEvents = new ArrayList<>(caseTimeline.get(eventTime));
                            partialOrderEvents.addAll(trace);
                            // get number of events in caseTimelines --> get also the ones that have the current timestamp
                            higherTimestamps = caseTimeline.tailMap(eventTime, true);
                            List<TrieNode> startingNodes = new ArrayList<>();
                            for (List<String> v : higherTimestamps.values()) {
                                numOfEventsToReplay = numOfEventsToReplay + v.size();
                            }
                            for (State s: currentStates.values()){
                                if (s.getTracePostfixSize()==numOfEventsToReplay){
                                    startingNodes.add(s.getNode());
                                }
                            }
                            backToTheOrderEvents = backToTheOrder(partialOrderEvents, startingNodes);
                            newTrace.addAll(backToTheOrderEvents);
                            if (!backToTheOrderEvents.equals(partialOrderEvents)){
                                if (adaptable) updateDecayTimeMultiplier(true);
                            }
                        } else {
                            // get number of events in caseTimelines --> only interested in the ones that have higher timestamp
                            higherTimestamps = caseTimeline.tailMap(eventTime, false);
                            newTrace.addAll(trace);
                            for (List<String> v : higherTimestamps.values()) {
                                numOfEventsToReplay = numOfEventsToReplay + v.size();
                            }
                            if (adaptable) updateDecayTimeMultiplier(true);
                        }

                        // remove either the state or the state suffix up to the number of events that need to be replayed
                        for (Iterator<Map.Entry<String, State>> states = currentStates.entrySet().iterator(); states.hasNext(); ) {
                            Map.Entry<String, State> entry = states.next();
                            if (entry.getValue().getTracePostfix().size() < numOfEventsToReplay) {
                                states.remove();
                            } else {
                                entry.getValue().removeTracePostfixTail(numOfEventsToReplay);
                            }
                        }

                        // generate a new trace that needs to be replayed
                        for (Map.Entry<Long, List<String>> entry : higherTimestamps.entrySet()) {
                            if (entry.getKey().equals(eventTime)){
                                continue; // these events have already been added above (newTrace.addAll(...))
                            }
                            List<String> value = entry.getValue();
                            newTrace.addAll(value);
                        }

//                    }
                    } else if (adaptable){ updateDecayTimeMultiplier(false); //event is in order, update the decay time multiplier
                    }

                    // update the map for case timelines
                    if (caseTimeline.containsKey(eventTime) & backToTheOrderType!=PartialOrderType.NONE) {
                        caseTimeline.remove(eventTime);
                        caseTimeline.put(eventTime, backToTheOrderEvents);
                    } else if (caseTimeline.containsKey(eventTime)){
                        caseTimeline.get(eventTime).addAll(trace);
                    } else {
                        caseTimeline.put(eventTime, trace);
                    }

                    List<Pair<String, Long>> activitiesTimestamps = caseTimelinePairs.getRight();
                    activitiesTimestamps.add(new MutablePair<>(trace.get(0), eventTime));

                    this.casesTimelines.put(caseId, new MutablePair<>(caseTimeline, activitiesTimestamps));
                } else {
                    TreeMap<Long, List<String>> caseTimeline = new TreeMap<>();
                    caseTimeline.put(eventTime, trace);
                    List<Pair<String, Long>> activitiesTimestamps = new ArrayList<>();
                    activitiesTimestamps.add(new MutablePair<>(trace.get(0), eventTime));
                    this.casesTimelines.put(caseId, new MutablePair<>(caseTimeline, activitiesTimestamps));
                }

                if (newTrace != null) {
                    trace = newTrace;
                }
            }

        }
        else
        {
            // if sync move(s) --> add sync move(s) to currentStates. If one of the moves will not be sync move, then start checking from that move.

            int decayTime = findDecayTime(0);
            currentStates.put(new Alignment().toString(), new State(new Alignment(), new ArrayList<String>(), modelTrie.getRoot(), 0, decayTime+1)); // larger decay time because this is decremented in this iteration

            if (eventTimeAware) {
                TreeMap<Long, List<String>> caseTimeline = new TreeMap<>();
                caseTimeline.put(eventTime, trace);
                List<Pair<String, Long>> activitiesTimestamps = new ArrayList<>();
                activitiesTimestamps.add(new MutablePair<>(trace.get(0), eventTime));

                this.casesTimelines.put(caseId, new MutablePair<>(caseTimeline, activitiesTimestamps));
            }

        }

        int traceCounter = 0;
        // Loop over all events (activities) in trace
        for(String event:trace){
            traceCounter++;
            // sync moves
            // we iterate over all states
            for (Iterator<Map.Entry<String, State>> states = currentStates.entrySet().iterator(); states.hasNext(); ) {

                Map.Entry<String, State> entry = states.next();
                previousState = entry.getValue();
                if (previousState.getTracePostfix().size()!=0){
                    continue; // we are not interested in previous states which already have a suffix (i.e. they already are non-synchronous)
                }

                state = checkForSyncMoves(event, previousState);
                if (state!=null){
                    // we are only interested in new states which are synced (i.e. they do not have a suffix)
                    syncMoveStates.add(state);
                }
            }


            // check if sync moves --> if yes, add sync states, update old states, remove too old states
            if (syncMoveStates.size() > 0){
                for (Iterator<Map.Entry<String, State>> states = currentStates.entrySet().iterator(); states.hasNext(); ) {
                    Map.Entry<String, State> entry = states.next();
                    previousState = entry.getValue();
                    int previousDecayTime = previousState.getDecayTime();
                    // remove states with decayTime less than 2
                    if (previousDecayTime < 2) {
                        states.remove();
                    } else {
                        List<String> postfix = new ArrayList<>();
                        postfix.add(event);
                        previousState.addTracePostfix(postfix);
                        if (trace.size()==traceCounter){ // this is for event aware solution not decrementing decay time multiple times
                            previousState.setDecayTime(previousDecayTime - 1);
                        }
                    }
                }

                for(State s:syncMoveStates){
                    alg = s.getAlignment();
                    currentStates.put(alg.toString(), s);
                }
                //caseStatesInBuffer.setCurrentStates(currentStates);
                //statesInBuffer.put(caseId, caseStatesInBuffer);
                //return currentStates;
                syncMoveStates.clear();
                continue;
            }


            // no sync moves. We iterate over the states, trying to make model and log moves
            HashMap<String, State> statesToIterate = new HashMap<>(currentStates);
            List<State> interimCurrentStates = new ArrayList<>();
            List<String> traceEvent = new ArrayList<>();
            traceEvent.add(event);
            int currentMinCost = 99999;
            for (Iterator<Map.Entry<String, State>> states = statesToIterate.entrySet().iterator(); states.hasNext(); ) {
                Map.Entry<String, State> entry = states.next();
                previousState = entry.getValue();


                State logMoveState = handleLogMove(traceEvent, previousState, "");

                traceSuffix = previousState.getTracePostfix();
                traceSuffix.addAll(traceEvent);
                List<State> modelMoveStates = handleModelMoves(traceSuffix, previousState, null);


                // add log move
                if(logMoveState.getCostSoFar() < currentMinCost){
                    interimCurrentStates.clear();
                    interimCurrentStates.add(logMoveState);
                    currentMinCost = logMoveState.getCostSoFar();
                } else if(logMoveState.getCostSoFar() == currentMinCost){
                    interimCurrentStates.add(logMoveState);
                }

                // add model moves
                for(State s:modelMoveStates){
                    if(s.getCostSoFar()< currentMinCost){
                        interimCurrentStates.clear();
                        interimCurrentStates.add(s);
                        currentMinCost = s.getCostSoFar();
                    } else if(s.getCostSoFar() == currentMinCost) {
                        interimCurrentStates.add(s);
                    }
                }


                int previousStateDecayTime = previousState.getDecayTime();
                if(previousStateDecayTime<2){
                    currentStates.remove(previousState.getAlignment().toString());
                } else {
                    if (trace.size()==traceCounter){ // this is for event aware solution not decrementing decay time multiple times
                        previousState.setDecayTime(previousStateDecayTime - 1);
                    }
                }

            }

            // add new states with the lowest cost
            for (State s:interimCurrentStates) {
                if (s.getCostSoFar() == currentMinCost) {
                    currentStates.put(s.getAlignment().toString(), s);
                }
            }


        }





        if(caseStatesInBuffer==null){
            caseStatesInBuffer = new StatesBuffer(currentStates);
        } else {
            caseStatesInBuffer.setCurrentStates(currentStates);
        }

        casesInBuffer.put(caseId, caseStatesInBuffer);

        // update the timelines -->
        if (eventTimeAware) {
            int eventsInBuffer = caseStatesInBuffer.getStateWithLargestSuffix().getTracePostfix().size();
            int eventsInTimelines = this.casesTimelines.get(caseId).getRight().size();
            Pair<TreeMap<Long, List<String>>, List<Pair<String, Long>>> caseTimelinePairs = null;
            TreeMap<Long, List<String>> caseTimeline = null;
            while (eventsInTimelines > eventsInBuffer) {
                caseTimelinePairs = this.casesTimelines.get(caseId);
                caseTimeline = caseTimelinePairs.getLeft();
                ConcurrentMap.Entry<Long,List<String>> earliestTime = caseTimeline.firstEntry();
                String earliestActivity = earliestTime.getValue().get(0);
                //remove the earliest activity
                if (earliestTime.getValue().size()>1){
                    earliestTime.getValue().remove(0);
                } else {
                    caseTimeline.remove(earliestTime.getKey());
                }
                // remove the element also from the right side of case timelines
                for (int i = 0; i<caseTimelinePairs.getRight().size();i++){
                    Pair<String, Long> p = caseTimelinePairs.getRight().get(i);
                    if (p.getRight().equals(earliestTime.getKey()) && p.getLeft().equals(earliestActivity)){
                        caseTimelinePairs.getRight().remove(i);
                        break;
                    }
                }

                eventsInTimelines--;
            }

            if (caseTimelinePairs != null && caseTimelinePairs.getRight().size() == 0) {
                this.casesTimelines.remove(caseId);
            } else if (caseTimelinePairs != null) {
                this.casesTimelines.put(caseId, caseTimelinePairs);
            }
        }

        handleCaseLimit(caseId);
        return currentStates;

    }





    protected List<State> handleModelMoves(List<String> traceSuffix, State state, State dummyState){
        TrieNode matchNode;
        Alignment alg;
        List<String> suffixToCheck = new ArrayList<>(); //make a new list and add to it
        suffixToCheck.addAll(traceSuffix);
        int lookAheadLimit = traceSuffix.size();
        List<TrieNode> currentNodes = new ArrayList<>();
        List<TrieNode> childNodes = new ArrayList<>();
        List<TrieNode> matchingNodes = new ArrayList<>();
        List<State> matchingStates = new ArrayList<>();
        currentNodes.add(state.getNode());

        while (lookAheadLimit>0) {
            // from current level, fetch all child nodes
            for(TrieNode n:currentNodes){
                childNodes.addAll(n.getAllChildren());
            }
            // for all child nodes, try to get a substring match
            for(TrieNode n:childNodes){
                matchNode = modelTrie.matchCompletely(suffixToCheck, n);
                if (matchNode!=null){
                    matchingNodes.add(matchNode);
                }
            }

            // something has matched, we will not look further
            if(matchingNodes.size()>0){
                break;
            }

            // no match, so child nodes become current nodes, and we reduce look ahead
            currentNodes.clear();
            currentNodes.addAll(childNodes);
            childNodes.clear();
            lookAheadLimit--;

            //if lookAhead is exhausted, but we can split suffix
            if (lookAheadLimit==0 & suffixToCheck.size()>1 & replayWithLogMoves){
                suffixToCheck.remove(0);
                lookAheadLimit = suffixToCheck.size();
                currentNodes.clear();
                currentNodes.add(state.getNode());
            }

        }

        if(matchingNodes.size()==0){
            //we didn't find any match, return empty array
        } else {
            // iterate back from matchingNode until parent = state.getNode
            // because we need correct alignment and cost
            for(TrieNode n:matchingNodes){
                alg = state.getAlignment();
                int cost = state.getCostSoFar();
                TrieNode currentNode = n;
                TrieNode parentNode = n.getParent();
                TrieNode lastMatchingNode = state.getNode();
                List<Move> moves = new ArrayList<>();
                boolean makeLogMoves = false;

                // first find all sync moves, then add model moves (parent does not match event), then add log moves (events still remaining in traceSuffix)
                for(int i = traceSuffix.size(); --i >= 0;){
                    String event = traceSuffix.get(i);
                    if(!currentNode.equals(lastMatchingNode)) {
                        if (event.equals(currentNode.getContent())) {
                            Move syncMove = new Move(event, event, 0);
                            moves.add(0, syncMove);
                            currentNode = parentNode;
                            parentNode = currentNode.getParent();
                            if (i > 0) {
                                continue; // there could still be more sync moves
                            }
                        } else {
                            makeLogMoves = true;
                        }
                    } else {
                        makeLogMoves = true;
                    }

                    // we either have a non-sync move or we have exhausted the suffix.
                    // so we need to add model moves (and log moves if applicable)

                    // we first iterate until we get to the lastMatchingNode
                    while(!currentNode.equals(lastMatchingNode)){
                        Move modelMove = new Move(">>",currentNode.getContent(),1);
                        cost++;
                        moves.add(0,modelMove);
                        currentNode = parentNode;
                        if(currentNode.getLevel()==0){ //we have reached the root node
                            break;
                        }
                        parentNode = currentNode.getParent();
                    }

                    // we also add all log moves now
                    while(makeLogMoves & i>=0) {
                        event = traceSuffix.get(i);
                        Move logMove = new Move(event, ">>", 1);
                        cost++;
                        moves.add(0, logMove);
                        i--;
                    }
                }

                // matching states
                for(Move m:moves){
                    alg.appendMove(m);
                }


                int decayTime = findDecayTime(alg.getTraceSize());

                matchingStates.add(new State(alg, new ArrayList<>(), n, cost, decayTime));

            }

        }

        return matchingStates;


    }



    @Override
    protected State handleLogMove(List<String> traceSuffix, State state, String event) {
        Alignment alg = new Alignment(state.getAlignment());
        State logMoveState;
        List<String> suffix = new ArrayList<>(state.getTracePostfix());
        suffix.addAll(traceSuffix);
        for (String e:suffix){
            Move logMove = new Move(e, ">>", 1);
            alg.appendMove(logMove);
        }

        int decayTime = findDecayTime(alg.getTraceSize());
        logMoveState = new State(alg, new ArrayList<String>(), state.getNode(), state.getCostSoFar()+suffix.size(), decayTime);
        return logMoveState;
    }
}
