package beamline.miners.trieconformance;

import beamline.miners.trieconformance.alignment.Alignment;
import beamline.miners.trieconformance.alignment.Move;
import beamline.miners.trieconformance.trie.Trie;
import beamline.miners.trieconformance.trie.TrieNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StreamingConformanceChecker extends ConformanceChecker{



    protected boolean verbose = false;

    protected LinkedHashSet<String> casesSeen;

    // Streaming variables

    protected boolean replayWithLogMoves = true;
    protected int minDecayTime; // = 3;
    protected float decayTimeMultiplier; // = 0.3F;
    protected boolean discountedDecayTime; // = true; // if set to false then uses fixed minDecayTime value
    protected int averageTrieLength;

    protected int stateLimit;
    protected int caseLimit;



    public StreamingConformanceChecker(Trie trie, int logCost, int modelCost, int stateLimit, int caseLimit, int minDecayTime, float decayTimeMultiplier, boolean discountedDecayTime)
    {
        super(trie, logCost, modelCost, stateLimit);
        this.stateLimit = stateLimit; //  to-be implemented
        this.caseLimit = caseLimit;
        this.minDecayTime = minDecayTime;
        this.decayTimeMultiplier = decayTimeMultiplier;
        this.discountedDecayTime = discountedDecayTime;

        if (this.discountedDecayTime){
            this.averageTrieLength = trie.getAvgTraceLength();
        }

        casesSeen = new LinkedHashSet<>();
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

                    decayTime = findDecayTime(s.getAlignment().getTraceSize());
                    if(s.getDecayTime() == decayTime & s.getTracePostfix().size()==0){
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



    public Alignment check(List<String> trace){
        System.out.println("Only implemented for compatibility with interface");
        return new Alignment();
    }

    public ConcurrentHashMap<String, State> check(List<String> trace, String caseId)
    {

        traceSize = trace.size();
        State state;
        State previousState;
        StatesBuffer caseStatesInBuffer = null;
        Alignment alg;
        TrieNode node;
        TrieNode prev;
        List<String> traceSuffix;
        int suffixLookAheadLimit;
        ConcurrentHashMap<String, State> currentStates = new ConcurrentHashMap<>();
        ArrayList<State> syncMoveStates = new ArrayList<>();

        // iterate over the trace - choose event by event
        // modify everything into accepting event instead of list of events

        if (casesInBuffer.containsKey(caseId))
        {
            // case exists, fetch last state
            caseStatesInBuffer = casesInBuffer.get(caseId);
            currentStates = caseStatesInBuffer.getCurrentStates();

        }
        else
        {
            // if sync move(s) --> add sync move(s) to currentStates. If one of the moves will not be sync move, then start checking from that move.

            int decayTime = findDecayTime(0);
            currentStates.put(new Alignment().toString(), new State(new Alignment(), new ArrayList<String>(), modelTrie.getRoot(), 0, decayTime+1)); // larger decay time because this is decremented in this iteration
        }

        for(String event:trace){
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
                        previousState.setDecayTime(previousDecayTime - 1);
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
                    previousState.setDecayTime(previousStateDecayTime-1);
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
