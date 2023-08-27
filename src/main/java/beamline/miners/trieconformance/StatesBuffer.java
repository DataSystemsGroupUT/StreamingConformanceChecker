package beamline.miners.trieconformance;


import java.util.HashMap;
import java.util.Map;

//

public class StatesBuffer {

    protected HashMap<String, State> currentStates;

    public StatesBuffer (String algString, State state){

        currentStates.put(algString, state);

    }

    public StatesBuffer (HashMap currentStates){

        this.currentStates = currentStates;

    }

    public void setCurrentStates(HashMap<String, State> currentStates){
        this.currentStates = currentStates;
    }

    public HashMap<String, State> getCurrentStates() {
        return currentStates;
    }
    public State getStateWithLargestSuffix() {
        State largestSuffixState = null;
        for(Map.Entry<String,State> entry : currentStates.entrySet()) {
            State value = entry.getValue();
            if (largestSuffixState == null) {
                largestSuffixState = value;
            } else {
                if (value.getTracePostfix().size() > largestSuffixState.getTracePostfix().size()) {
                    largestSuffixState = value;
                }
            }
        }
        return largestSuffixState;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (State s:currentStates.values()){
            result.append(s.toString());
        }
        return result.toString();
    }

}
