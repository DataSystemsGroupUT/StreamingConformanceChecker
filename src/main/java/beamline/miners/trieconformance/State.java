package beamline.miners.trieconformance;

import beamline.miners.trieconformance.alignment.Alignment;
import beamline.miners.trieconformance.trie.TrieNode;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;


public class State implements Comparable<State>, Serializable {
    private Alignment alignment;
    private List<String> tracePostfix;
    private TrieNode node;
    private int costSoFar;
    private double weightedSumOfCosts;
    private int completenessCost = 0;
    private State parentState;
    private int decayTime;

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, int costSoFar)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.costSoFar = costSoFar;
        this.parentState = null;
        this.decayTime = 999999;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, int costSoFar, final State parentState)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.costSoFar = costSoFar;
        this.parentState = parentState;
        this.decayTime = 999999;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, int costSoFar, int decayTime)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.costSoFar = costSoFar;
        this.parentState = null;
        this.decayTime = decayTime;
    }

    // This new constructor was added to link back to previous states and track the cost of partial alignments
    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, int costSoFar, final State parentState, int decayTime)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.costSoFar = costSoFar;
        this.parentState = parentState;
        this.decayTime = decayTime;
    }

    // Added for weighted cost and completeness

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, double weightedSumOfCosts, int decayTime)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.weightedSumOfCosts = weightedSumOfCosts;
        this.parentState = null;
        this.decayTime = decayTime;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, double weightedSumOfCosts, int decayTime, int completenessCost)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.weightedSumOfCosts = weightedSumOfCosts;
        this.parentState = null;
        this.decayTime = decayTime;
        this.completenessCost = completenessCost;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, double weightedSumOfCosts, final State parentState, int decayTime, int completenessCost)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.weightedSumOfCosts = weightedSumOfCosts;
        this.parentState = parentState;
        this.decayTime = decayTime;
        this.completenessCost = completenessCost;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, double weightedSumOfCosts, final State parentState, int decayTime)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.weightedSumOfCosts = weightedSumOfCosts;
        this.parentState = parentState;
        this.decayTime = decayTime;
    }

    public State(Alignment alignment, List<String> tracePostfix, TrieNode node, double weightedSumOfCosts)
    {
        this.alignment = alignment;
        this.tracePostfix = new LinkedList<>();
        this.tracePostfix.addAll(tracePostfix);
        this.node = node;
        this.weightedSumOfCosts = weightedSumOfCosts;
        this.parentState = null;
        this.decayTime = 999999;
    }

    @Override
    public int compareTo(State other)
    {
        if (this.costSoFar > other.getCostSoFar())
            return 1;
        else if (this.costSoFar < other.getCostSoFar())
            return -1;
        else
            return 0;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(String.format("Total cost: %d\n", costSoFar));
        result.append(String.format("Current node: %s\n", node.getContent()));
        result.append(String.format("Alignment: %s\n", alignment));
        result.append(String.format("Suffix: %s\n", tracePostfix));
        result.append(String.format("Decay time: %d\n", decayTime));
        return result.toString();
    }

    public void addTracePostfix(List <String> tracePostfix)
    {
        this.tracePostfix.addAll(tracePostfix);
    }

    public Alignment getAlignment() {
        return new Alignment(alignment);
    }

    public List<String> getTracePostfix() {
        return tracePostfix;
    }

    public void removeTracePostfixTail(int numOfEventsToRemove) {
        int counter = 0;
        for (int i = tracePostfix.size() - 1; i >= 0; i--) {
            tracePostfix.remove(i);
            counter++;
            if (counter >= numOfEventsToRemove){ break; }
        }
    }

    public TrieNode getNode() {
        return node;
    }

    public int getCostSoFar() {
        return costSoFar;
    }

    public int hashCode()
    {
        return node.hashCode();
    }

    public State getParentState()
    {
        return parentState;
    }

    public int getDecayTime() { return decayTime;}

    public void setDecayTime(int decayTime) {
        this.decayTime = decayTime;
    }

    public double getWeightedSumOfCosts() {
        return weightedSumOfCosts;
    }

    public int getCompletenessCost() {
        return completenessCost;
    }

    public void setAlignment(Alignment a){
        this.alignment = a;
    }
}