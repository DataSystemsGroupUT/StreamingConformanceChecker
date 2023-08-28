package beamline.miners.trieconformance.trie;

import beamline.miners.trieconformance.State;
import beamline.miners.trieconformance.util.Utils;

import java.util.*;
import java.io.Serializable;

public class TrieNode implements Serializable {

    private String content; // This is a numerical representation of the activity label. Should be part of a lookup table
    private int maxChildren;
    private int minPathLengthToEnd;
    private int maxPathLengthToEnd;
    private TrieNode parent;
    private HashMap<String, TrieNode> children;
    private boolean isEndOfTrace;
    private List<Integer> linkedTraces;
    private int level=0;
    private int numChildren=0;

    public State getAlignmentState() {
        return alignmentState;
    }

    public void setAlignmentState(State alignmentState) {
        this.alignmentState = alignmentState;
    }

    private State alignmentState;

    public int getMaxChildren() {
        return maxChildren;
    }

    public void setMaxChildren(int maxChildren) {
        this.maxChildren = maxChildren;
    }

    public void setMinPathLengthToEnd(int minPathLengthToEnd) {
        this.minPathLengthToEnd = minPathLengthToEnd;
    }

    public void setMaxPathLengthToEnd(int maxPathLengthToEnd) {
        this.maxPathLengthToEnd = maxPathLengthToEnd;
    }

    public void setParent(TrieNode parent) {
        this.parent = parent;
    }

    public HashMap<String, TrieNode> getChildren() {
        return children;
    }

    public void setChildren(HashMap<String, TrieNode> children) {
        this.children = children;
    }

    public void setLinkedTraces(List<Integer> linkedTraces) {
        this.linkedTraces = linkedTraces;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getNumChildren() {
        return numChildren;
    }

    public void setNumChildren(int numChildren) {
        this.numChildren = numChildren;
    }

    public void setContent(String content) {
        this.content = content;
    }

    private void setEndOfTrace(boolean isEndOfTrace)
    {
        this.isEndOfTrace = isEndOfTrace;
    }
//    public TrieNode(String content, int maxChildren, int minPathLengthToEnd, boolean isEndOfTrace, TrieNode parent)
//    {
//        this.content = content;
//        this.maxChildren = Utils.isPrime(maxChildren)? maxChildren:  Utils.nextPrime(maxChildren);
//        //TODO: Change children type to HashMap?
//        this.children = new TrieNode[this.maxChildren];
//        this.minPathLengthToEnd = minPathLengthToEnd;
//        this.parent = parent;
//
//        this.isEndOfTrace = isEndOfTrace;
//        this.linkedTraces = new ArrayList<>();
//    }

    public TrieNode(){}

    public TrieNode(String content, int maxChildren, int minPathLengthToEnd, int maxPathLengthToEnd, boolean isEndOfTrace, TrieNode parent)
    {
        this.content = content;
        this.maxChildren = Utils.isPrime(maxChildren)? maxChildren:  Utils.nextPrime(maxChildren);
        //TODO: Change children type to HashMap?
        this.children = new HashMap<String, TrieNode>();
        this.minPathLengthToEnd = minPathLengthToEnd;
        this.maxPathLengthToEnd = maxPathLengthToEnd;
        this.parent = parent;
        if(parent != null)
            this.level = parent.getLevel()+1;

        this.isEndOfTrace = isEndOfTrace;
        this.linkedTraces = new ArrayList<>();
    }

    public int getLevel() {
        return level;
    }

    public void addLinkedTraceIndex(int i )
    {
        this.linkedTraces.add(i);
    }
    public List<Integer> getLinkedTraces()
    {
        return linkedTraces;
    }
    public String getContent()
    {
        return  content;
    }

    public int getMinPathLengthToEnd() {
        return minPathLengthToEnd;
    }

    public int getMaxPathLengthToEnd()
    {
        return maxPathLengthToEnd;
    }

    public TrieNode getParent()
    {
        return parent;
    }
    public TrieNode getChild(String label)
    {
        // TODO: iterate over children and get node label?
        TrieNode result = children.get(label);
//        if (result != null) //&& !result.getContent().equals(label))
//        {
//            //System.err.println(String.format("Different labels with the same hash code %s and %s", result.getContent(), label));
//            result = null;
//        }
        return result ;
    }

    public boolean isEndOfTrace() {
        return isEndOfTrace;
    }

    public TrieNode getChildWithLeastPathLengthDifference( int pathLength) {
        int minPath = pathLength;
        TrieNode child = null;
        int minDiff = Integer.MAX_VALUE;

        for (Map.Entry<String, TrieNode> entry : children.entrySet()) {
            TrieNode ch = entry.getValue();
            if (ch != null) {
                int diff = Math.abs(ch.getMinPathLengthToEnd() - minPath);
                if (diff < minDiff) {
                    child = ch;
                    minDiff = diff;
                }

            }
        }
        return child;
    }

    public List<TrieNode> getAllChildren()
    {
        List<TrieNode> result = new ArrayList<>();
        for (Map.Entry<String,TrieNode> entry : children.entrySet()) {
            TrieNode ch = entry.getValue();
            result.add(ch);
        }
        return result;
    }

    public boolean hasChildren()
    {
        return numChildren != 0;
    }


    public TrieNode addChild(TrieNode child)
    {
        if (children.get(child.getContent()) == null) {
            children.put(child.getContent(), child);
            child.parent = this;
            numChildren++;
        } else if (child.isEndOfTrace()) {
            children.get(child.getContent()).setEndOfTrace(child.isEndOfTrace());
        }
        this.minPathLengthToEnd = Math.min(this.minPathLengthToEnd, child.getMinPathLengthToEnd() + 1);
        this.maxPathLengthToEnd = Math.min(this.maxPathLengthToEnd, child.getMaxPathLengthToEnd() + 1);

        return children.get(child.getContent());
    }

    public String toString()
    {
        /*StringBuilder result = new StringBuilder();
        result.append(" Node(content:"+this.content+", minPath:"+minPathLengthToEnd+", maxPath:"+maxPathLengthToEnd+", isEndOfATrace:"+isEndOfTrace+") Children(");
        for (TrieNode child : children)
            if (child != null)
                result.append(child.toString());
        result.append(")");
        return result.toString();*/
        return this.getPrefix();
    }

    public boolean equals(Object other)
    {
        if (other instanceof  TrieNode)
        {
            TrieNode otherNode = (TrieNode) other;

            if(this.content.equals(otherNode.getContent()) && this.level==otherNode.getLevel()){
                if(this.getPrefix().equals(otherNode.getPrefix())){
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    public String getPrefix()
    {
        if(this.getLevel()==0)
            return "";

        StringBuilder result = new StringBuilder();
        List<String> prefix = new ArrayList<>();
        TrieNode currentNode = this.getParent();

        while(currentNode.getLevel()>0){
            prefix.add(0,currentNode.getContent());
            currentNode = currentNode.getParent();
        }
        for(String p:prefix){
            result.append(p+"->");
        }
        result.append(this.getContent());
        return result.toString();
    }

    public int hashCode()
    {
        return this.getPrefix().hashCode();
    }

    public TrieNode getChildOnShortestPathToTheEnd()
    {
        TrieNode child;
        child = this;

        for (Map.Entry<String,TrieNode> entry : children.entrySet()) {
            TrieNode ch = entry.getValue();
            if (ch==null)
                continue;
            if (ch.isEndOfTrace()){
                child = ch;
                break;
            }
            if (ch.getMinPathLengthToEnd() < child.getMinPathLengthToEnd())
                child = ch;
        }
        if (child == this)
            return null;
        else
            return  child;
    }

}
