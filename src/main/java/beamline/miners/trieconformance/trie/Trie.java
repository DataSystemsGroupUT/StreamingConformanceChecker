package beamline.miners.trieconformance.trie;



import beamline.miners.trieconformance.util.Utils;

import java.io.Serializable;
import java.util.*;
public class Trie implements Serializable {

    private final TrieNode root;
    private List<TrieNode> leaves;
    private final int maxChildren;
    private int internalTraceIndex=0;
    private int size=0;
    private int numberOfEvents=0;
    protected HashMap<Integer,String> traceIndexer;
    private int maxPatternSize;
    private HashMap<List<String>, Integer> patternFrequency;
    private List<Map.Entry<List<String>, Integer>> patternFrequencySorted;

    public Trie(int maxChildren, int maxPatternSize)
    {
        this.maxChildren = Utils.nextPrime(maxChildren);
        this.maxPatternSize = maxPatternSize;
        root = new TrieNode("dummy", maxChildren, Integer.MAX_VALUE, Integer.MIN_VALUE, false,null);
        traceIndexer = new HashMap<>();
        leaves = new ArrayList<>();
        patternFrequency = new HashMap<>();
        // patternFrequencyQueue will be a priority queue that is sorted by the value of the map
        // this is so that we can loop over the patterns starting from the most frequent ones.
        patternFrequencySorted = new ArrayList<>();
    }
    public int getMaxChildren()
    {
        return maxChildren;
    }
    public void addTrace(List<String> trace)
    {
        ++internalTraceIndex;
        addTrace(trace, internalTraceIndex);

    }
    public void addTrace(List<String> trace, int traceIndex) {
        TrieNode current = root;
        int minLengthToEnd = trace.size();
        if (minLengthToEnd > 0)
        {
            StringBuilder sb = new StringBuilder(trace.size());
            for (String event : trace)
            {
                current.addLinkedTraceIndex(traceIndex);
                TrieNode child = new TrieNode(event,maxChildren,minLengthToEnd-1, minLengthToEnd-1, minLengthToEnd-1==0? true: false,current);
                TrieNode returned;
                returned = current.addChild(child);
                if (returned  == child ) // we added a new node to the trie
                {
                    size++;
                }
                current = returned;
                minLengthToEnd--;
                sb.append(event);
                if (returned.isEndOfTrace())
                    leaves.add(returned);
            }
            current.addLinkedTraceIndex(traceIndex);
            numberOfEvents+=sb.length();
            traceIndexer.put(traceIndex, sb.toString());
            updatePatternFrequency(trace);
        }

    }

    private void updatePatternFrequency(List<String> trace) {
        // this method updates the pattern frequency. lengthLimit allows to limit the size of the patterns
        int lengthLimit = Math.min(trace.size(), maxPatternSize);
        for (int length = 2; length <= lengthLimit; length++) {
            for (int start = 0; start <= trace.size() - length; start++) {
                List<String> pattern = new ArrayList<>();
                for (int i = start; i < start + length; i++) {
                    pattern.add(trace.get(i));
                }
                patternFrequency.put(pattern, patternFrequency.getOrDefault(pattern, 0) + 1);
            }
        }
    }

    public List<TrieNode> matchInHops(String evt, TrieNode startFromThisNode, int maxHops){
        List<TrieNode> matches = new ArrayList<>();
        List<TrieNode> subMatches;
        TrieNode currentNode = startFromThisNode;
        TrieNode matchedNode;
        matchedNode = currentNode.getChild(evt);
        if (matchedNode!=null){
            matches.add(matchedNode);
        }
        if (maxHops>1){
            for(TrieNode c:currentNode.getAllChildren()) {
                subMatches = matchInHops(evt, c, maxHops - 1);
                if (subMatches != null){
                    matches.addAll(subMatches);
                }

            }
        }
        // dilemma: make this return null or empty list?
        if (matches.size()==0){
            matches = null;
        }
        return matches;
    }

    public List getPatternFrequencySorted(){
        if (patternFrequencySorted.size()==0) {
            patternFrequencySorted.addAll(patternFrequency.entrySet());
            patternFrequencySorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        }
        return patternFrequencySorted;
    }
    public TrieNode getRoot()
    {
        return root;
    }
    public String toString()
    {

            return root.toString();


    }
    public String getTrace(int index)
    {
        return traceIndexer.get(index);
    }

    public void printTraces()
    {
//        StringBuilder result = new StringBuilder();
//        TrieNode current;
//        for (TrieNode  leaf: leaves)
//        {
//            current = leaf;
//            result = new StringBuilder();
//            while (current != root)
//            {
//                result.append(current.getContent()+",");
//                current = current.getParent();
//            }
//
//            System.out.println(result.reverse().toString());
//        }
        for (String s: traceIndexer.values())
            System.out.println(s);
    }
    /**
     * This method finds the deepest node in the trie that provides the longest prefix match to the trace.
     * If there is no match at all, the method returns null.
     * @param trace is a list of strings that define the trace to search a match for
     * @return a trie node
     */
    public TrieNode match(List<String> trace, TrieNode startFromThisNode)
    {
        TrieNode current = startFromThisNode;
        TrieNode result;
        int size = trace.size();
        int lengthDifference = Integer.MAX_VALUE;
        for(int i = 0; i < size; i++)
//        for (String event : trace)
        {
            result = current.getChild(trace.get(i));
            // result = current.getChildWithLeastPathLengthDifference(trace.get(i), size - i);

            if (result == null && current == startFromThisNode)
                return null;
            else if (result == null)
                return current;
            else {
                TrieNode result2 = result;
                //result2 = result.getChildWithLeastPathLengthDifference(size-(i+1));

//                if (Math.abs(result2.getMinPathLengthToEnd() - (size - (i+1))) <= lengthDifference)
                //               {
                // we still have a promising direction
                current = result;
//                    lengthDifference = Math.abs(result.getMinPathLengthToEnd() - (size - (i+1)));
//                }
//                else
//                    return current.getParent();


            }
        }
        return current;
    }
    public TrieNode match(List<String> trace)
    {
        return match(trace, root);
    }

    public TrieNode matchCompletely(List<String> trace, TrieNode startFromThisNode)
    {
        TrieNode current = startFromThisNode;
        TrieNode result;
        int size = trace.size();
        for(int i = 0; i < size; i++)
        {
            result = current.getChild(trace.get(i));
            if (result == null)
                return null;
            else {
                TrieNode result2 = result;
                current = result;
            }
        }
        return current;
    }

    public int getMaxTraceLength()
    {
        int maxLength = leaves.stream().map( node -> node.getLevel()).reduce(Integer.MIN_VALUE, (minSoFar, element) -> Math.max(minSoFar, element));
        return maxLength;
    }

    public int getMinTraceLength()
    {
        int minLength =leaves.stream().map( node -> node.getLevel()).reduce(Integer.MAX_VALUE, (minSoFar, element) -> Math.min(minSoFar, element));
        return minLength;
    }


    public int getAvgTraceLength()
    {
        int sumlength = leaves.stream().map( node -> node.getLevel()).reduce(0, (subtotal, element) -> subtotal+element);



       return  sumlength/leaves.size();
    }

    public int getSize()
    {
        return size;
    }

    public int getNumberOfEvents()
    {
        return numberOfEvents;
    }

    public TrieNode getNodeOnShortestTrace()
    {
        int currentMinLevel = 99999;
        TrieNode currentMinNode = null;
        for (TrieNode n:leaves){
            if(n.getLevel()<currentMinLevel){
                currentMinNode = n;
                currentMinLevel = n.getLevel();
            }
        }
        return currentMinNode;
    }

    public List<TrieNode> getLeavesFromNode(TrieNode startNode, int maxLevel){
        List <TrieNode> result = new ArrayList<>();
        TrieNode currentNode;
        int startNodeLevel = startNode.getLevel();
        int currentNodeLevel;
        for (TrieNode n:leaves){

            currentNodeLevel = n.getLevel();
            currentNode = n;
            if(currentNodeLevel>startNodeLevel & n.getLevel()<=maxLevel){
                if(result.contains(n)){
                    continue;
                }
                while(currentNodeLevel>startNodeLevel){
                    currentNode = currentNode.getParent();
                    currentNodeLevel = currentNode.getLevel();
                    if(currentNodeLevel==startNodeLevel & currentNode==startNode){
                        result.add(n);
                    }
                }
            }
        }
        return result;
    }

}
